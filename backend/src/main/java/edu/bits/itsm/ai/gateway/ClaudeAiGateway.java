package edu.bits.itsm.ai.gateway;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * <b>Adapter</b> from {@link AiGateway} onto the official Anthropic Java SDK: the only class that talks to Claude.
 * <ul>
 *   <li>Key: read once from the {@code ANTHROPIC_API_KEY} environment variable, never from configuration, never logged.</li>
 *   <li>Request: the configured model, effort {@code low}, no {@code thinking} field (the model runs adaptive), a JSON
 *       schema as structured output ({@code output_config.format}), images as base64 blocks.</li>
 *   <li>Retry: after a 408, 429 or 5xx answer or a failed connection, one more try half a second later, but only while
 *       at least half of the feature's time budget is left, and only for the rest of it. So a call never waits longer
 *       than its budget, and a call that ran out of time is not tried again. The SDK's own retries stay off: they
 *       would give a timed-out call a second full budget and wait whatever Retry-After the server asks for.</li>
 *   <li>Refusals: the server-side fallback ({@code fallbacks: "default"}, beta {@code server-side-fallback-2026-07-01})
 *       is sent; if this key may not use it, it is dropped for the rest of the run. A final {@code refusal} stop
 *       reason becomes {@link AiFailure#REFUSED}, so the caller uses its rules answer.</li>
 * </ul>
 */
@Component
public class ClaudeAiGateway implements AiGateway {

    static final String API_KEY_ENV = "ANTHROPIC_API_KEY";
    /** Short structured answers; the per-feature timeout is what really bounds a call. */
    static final long MAX_TOKENS = 2048;
    /** Wait before the one retry. */
    static final Duration RETRY_PAUSE = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiGateway.class);

    private final AiProperties properties;
    private final ObjectMapper json;
    private final AnthropicClient client;          // null when no key is configured
    private final Duration retryPause;
    private volatile boolean serverFallback = true;

    @Autowired
    public ClaudeAiGateway(AiProperties properties, ObjectMapper json) {
        this(properties, json, System.getenv(API_KEY_ENV), null, RETRY_PAUSE);
    }

    /** For tests: this key and base URL (a local fake server) and this pause; a null base URL means Anthropic's API. */
    ClaudeAiGateway(AiProperties properties, ObjectMapper json, String key, String baseUrl, Duration retryPause) {
        this.properties = properties;
        this.json = json;
        this.retryPause = retryPause;
        this.client = key == null || key.isBlank() ? null : client(key.strip(), baseUrl);
    }

    private static AnthropicClient client(String key, String baseUrl) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(key).maxRetries(0).timeout(Duration.ofSeconds(10));
        if (baseUrl != null) builder.baseUrl(baseUrl);
        return builder.build();
    }

    @Override
    public boolean hasCredentials() { return client != null; }

    @Override
    public JsonNode complete(AiRequest request) {
        if (client == null) throw new AiGatewayException(AiFailure.KEY_INVALID, API_KEY_ENV + " is not set");
        try {
            return read(sendWithOneRetry(request, properties.timeoutFor(request.feature())));
        } catch (AiGatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ClaudeErrorClassifier.toGatewayException(ex);
        }
    }

    /** Sends; if the error is worth a retry and at least half of {@code budget} is left, sends once more within the rest. */
    private BetaMessage sendWithOneRetry(AiRequest request, Duration budget) {
        long start = System.nanoTime();
        try {
            return send(request, budget);
        } catch (RuntimeException ex) {
            Duration left = budget.minusNanos(System.nanoTime() - start).minus(retryPause);
            if (!ClaudeErrorClassifier.worthRetrying(ex) || left.compareTo(budget.dividedBy(2)) < 0) throw ex;
            log.info("AI {} call failed ({}); trying once more", request.feature(), ex.getClass().getSimpleName());
            if (!pause(retryPause)) throw ex;
            return send(request, left);
        }
    }

    /** False when the thread is interrupted (for example at shutdown): then there is no second try. */
    private static boolean pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private BetaMessage send(AiRequest request, Duration timeout) {
        RequestOptions options = RequestOptions.builder().timeout(timeout).build();
        boolean withFallback = serverFallback;
        try {
            return client.beta().messages().create(params(request, withFallback), options);
        } catch (BadRequestException ex) {
            if (!withFallback || !ClaudeErrorClassifier.rejectsServerFallback(ClaudeErrorClassifier.describe(ex))) throw ex;
            serverFallback = false;
            log.warn("Server-side refusal fallback is not available for this API key; refusals now use the rules fallback only");
            return client.beta().messages().create(params(request, false), options);
        }
    }

    /** The exact SDK request for one call (package-private so a test can check its JSON without the network). */
    MessageCreateParams params(AiRequest request, boolean withFallback) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(MAX_TOKENS)
                .system(request.system())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.LOW)
                        .format(BetaJsonOutputFormat.builder().schema(schema(request.schema())).build())
                        .build());
        for (AiMessage m : request.messages()) {
            if (m.role() == AiMessage.Role.ASSISTANT) b.addAssistantMessage(m.text());
            else if (m.image() == null) b.addUserMessage(m.text());
            else b.addUserMessageOfContentBlockParams(List.of(image(m.image()), BetaContentBlockParam.ofText(m.text())));
        }
        if (withFallback) b.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacksDefault();
        return b.build();
    }

    private static BetaJsonOutputFormat.Schema schema(Map<String, Object> schema) {
        BetaJsonOutputFormat.Schema.Builder s = BetaJsonOutputFormat.Schema.builder();
        schema.forEach((k, v) -> s.putAdditionalProperty(k, JsonValue.from(v)));
        return s.build();
    }

    private static BetaContentBlockParam image(AiMessage.Image image) {
        return BetaContentBlockParam.ofImage(BetaImageBlockParam.builder()
                .source(BetaBase64ImageSource.builder()
                        .mediaType(BetaBase64ImageSource.MediaType.of(image.mediaType()))
                        .data(image.base64())
                        .build())
                .build());
    }

    /** Checks the stop reason before the content: a refusal or a cut-off answer is not a usable answer. */
    private JsonNode read(BetaMessage reply) {
        BetaStopReason stop = reply.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stop)) throw new AiGatewayException(AiFailure.REFUSED, "Claude declined the request");
        if (BetaStopReason.MAX_TOKENS.equals(stop)) throw new AiGatewayException(AiFailure.BAD_OUTPUT, "Claude's answer was cut off");
        StringBuilder text = new StringBuilder();
        for (BetaContentBlock block : reply.content()) {
            if (block.fallback().isPresent()) text.setLength(0);   // a fallback model took over mid-answer: keep only its text
            block.text().map(BetaTextBlock::text).ifPresent(text::append);
        }
        try {
            JsonNode node = json.readTree(text.toString());
            if (node == null || !node.isObject()) throw new AiGatewayException(AiFailure.BAD_OUTPUT, "Claude's answer was not a JSON object");
            return node;
        } catch (JsonProcessingException ex) {
            throw new AiGatewayException(AiFailure.BAD_OUTPUT, "Claude's answer was not valid JSON", ex);
        }
    }
}
