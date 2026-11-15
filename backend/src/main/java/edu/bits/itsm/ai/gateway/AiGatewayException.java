package edu.bits.itsm.ai.gateway;

import java.util.Objects;

/** Thrown by an {@link AiGateway} when a call does not produce a usable answer; carries the {@link AiFailure} kind. */
public class AiGatewayException extends RuntimeException {

    private final AiFailure failure;

    public AiGatewayException(AiFailure failure, String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public AiGatewayException(AiFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public AiFailure failure() { return failure; }
}
