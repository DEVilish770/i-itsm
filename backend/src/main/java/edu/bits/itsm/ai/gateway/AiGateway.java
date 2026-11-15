package edu.bits.itsm.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * <b>Adapter</b> port: the only way the application talks to a language model. {@link ClaudeAiGateway} adapts the
 * Anthropic SDK onto it and tests plug in a fake. A gateway never decides on modes or fallbacks: it answers or throws.
 * Callers go through {@link edu.bits.itsm.ai.AiModeService}, never straight to a gateway.
 */
public interface AiGateway {

    /** True when credentials are configured; without them the features run in rules mode. */
    boolean hasCredentials();

    /**
     * Sends one request and returns the model's structured answer, a JSON object matching {@link AiRequest#schema()}.
     *
     * @throws AiGatewayException when no usable answer comes back
     */
    JsonNode complete(AiRequest request);
}
