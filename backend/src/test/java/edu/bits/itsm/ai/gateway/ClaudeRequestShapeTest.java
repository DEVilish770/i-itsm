package edu.bits.itsm.ai.gateway;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TC-78: the request the Claude adapter would send, serialised by the SDK itself and checked field by field (no network). */
class ClaudeRequestShapeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ClaudeAiGateway gateway = new ClaudeAiGateway(AiTestSupport.properties(true, 150), json);

    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")),
            "required", List.of("q"), "additionalProperties", false);

    private JsonNode body(MessageCreateParams params) throws Exception {
        return json.readTree(ObjectMappers.jsonMapper().writeValueAsString(params._body()));
    }

    @Test @DisplayName("TC-78 Wire request: model, effort low, JSON-schema output, base64 image, server-side fallback; no thinking, no tool_choice")
    void requestShape() throws Exception {
        AiRequest request = new AiRequest(AiFeature.SCREENSHOT_TRIAGE, "SYSTEM TEXT", List.of(
                AiMessage.userWithImage(new AiMessage.Image("image/png", AiTestSupport.PNG_BASE64), "Here is the screenshot"),
                AiMessage.assistant("Which service?"),
                AiMessage.user("The VPN")), SCHEMA);

        MessageCreateParams withFallback = gateway.params(request, true);
        JsonNode b = body(withFallback);
        assertEquals("claude-sonnet-5-5", b.get("model").asText());
        assertEquals(ClaudeAiGateway.MAX_TOKENS, b.get("max_tokens").asLong());
        assertEquals("SYSTEM TEXT", b.get("system").asText());
        assertFalse(b.has("thinking"), "thinking is omitted entirely (disabled is a 400 on this model)");
        assertFalse(b.has("tool_choice"));
        assertEquals("low", b.at("/output_config/effort").asText());
        assertEquals("json_schema", b.at("/output_config/format/type").asText());
        assertEquals(json.valueToTree(SCHEMA), b.at("/output_config/format/schema"));
        assertEquals("default", b.get("fallbacks").asText());
        assertEquals(List.of(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01), withFallback.betas().orElseThrow());

        JsonNode image = b.at("/messages/0/content/0");
        assertEquals("image", image.get("type").asText());
        assertEquals("base64", image.at("/source/type").asText());
        assertEquals("image/png", image.at("/source/media_type").asText());
        assertEquals(AiTestSupport.PNG_BASE64, image.at("/source/data").asText());
        assertEquals("Here is the screenshot", b.at("/messages/0/content/1/text").asText());
        assertEquals("assistant", b.at("/messages/1/role").asText());
        assertEquals("user", b.at("/messages/2/role").asText());

        MessageCreateParams plain = gateway.params(request, false);
        assertFalse(body(plain).has("fallbacks"));
        assertTrue(plain.betas().map(List::isEmpty).orElse(true));
    }
}
