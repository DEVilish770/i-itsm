package edu.bits.itsm.ai.gateway;

import java.util.Objects;

/** One turn of a conversation sent to the model. Only a user turn may carry an image. */
public record AiMessage(Role role, String text, Image image) {

    public enum Role { USER, ASSISTANT }

    /** A base64-encoded image (no {@code data:} prefix) with its media type, e.g. {@code image/png}. */
    public record Image(String mediaType, String base64) { }

    public AiMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
        if (image != null && role != Role.USER) throw new IllegalArgumentException("Only a user turn can carry an image");
    }

    public static AiMessage user(String text) { return new AiMessage(Role.USER, text, null); }
    public static AiMessage assistant(String text) { return new AiMessage(Role.ASSISTANT, text, null); }
    public static AiMessage userWithImage(Image image, String text) { return new AiMessage(Role.USER, text, Objects.requireNonNull(image)); }
}
