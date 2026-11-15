package edu.bits.itsm.ai.feature;

import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.exception.InvalidAiRequestException;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * A validated error screenshot (value object): one of four image types, real base64 without a {@code data:} prefix,
 * at most 5 MB once decoded, and bytes whose signature matches the declared type. Built only through {@link #of}.
 */
public final class Screenshot {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    static final Set<String> MEDIA_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final int MAX_BASE64_CHARS = 4 * ((MAX_BYTES + 2) / 3);

    private final String mediaType;
    private final String base64;

    private Screenshot(String mediaType, String base64) {
        this.mediaType = mediaType;
        this.base64 = base64;
    }

    /** @throws InvalidAiRequestException when the image breaks any of the rules above */
    public static Screenshot of(String imageBase64, String mediaType) {
        String type = mediaType == null ? "" : mediaType.strip().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(type))
            throw new InvalidAiRequestException("mediaType must be one of image/png, image/jpeg, image/webp, image/gif");
        if (imageBase64 == null || imageBase64.isBlank()) throw new InvalidAiRequestException("imageBase64 is required");
        String data = imageBase64.strip();
        if (data.startsWith("data:")) throw new InvalidAiRequestException("Send imageBase64 without the data: prefix");
        if (data.length() > MAX_BASE64_CHARS) throw new InvalidAiRequestException("The screenshot is larger than 5 MB");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new InvalidAiRequestException("imageBase64 is not valid base64");
        }
        if (bytes.length > MAX_BYTES) throw new InvalidAiRequestException("The screenshot is larger than 5 MB");
        if (!signatureMatches(type, bytes)) throw new InvalidAiRequestException("The image content is not a " + type + " file");
        return new Screenshot(type, data);
    }

    AiMessage.Image asImage() { return new AiMessage.Image(mediaType, base64); }

    public String mediaType() { return mediaType; }

    /** Checks the first bytes ("magic number") of the file format. */
    private static boolean signatureMatches(String type, byte[] b) {
        return switch (type) {
            case "image/png" -> startsWith(b, 0, 0x89, 'P', 'N', 'G');
            case "image/jpeg" -> startsWith(b, 0, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> startsWith(b, 0, 'G', 'I', 'F', '8');
            case "image/webp" -> startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[offset + i] & 0xFF) != expected[i]) return false;
        return true;
    }
}
