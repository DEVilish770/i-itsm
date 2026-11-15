package edu.bits.itsm.ai.gateway;

import edu.bits.itsm.ai.AiFeature;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One self-contained model request: which feature asks, the system instructions (trusted), the conversation (untrusted
 * user data) and the JSON schema the answer must follow (sent as a structured-output format).
 */
public record AiRequest(AiFeature feature, String system, List<AiMessage> messages, Map<String, Object> schema) {

    public AiRequest {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(schema, "schema");
        messages = List.copyOf(messages);
        if (messages.isEmpty() || messages.get(0).role() != AiMessage.Role.USER)
            throw new IllegalArgumentException("A conversation starts with a user turn");
    }
}
