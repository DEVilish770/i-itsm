package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Reads single fields of a model's JSON answer and validates them. Structured outputs already follow the schema, but
 * nothing from a model is trusted: every reader returns null for a missing, mistyped or invalid value.
 */
final class ModelOutput {

    private ModelOutput() { }

    /** Trimmed text, cut to {@code max} characters; null when missing or blank. */
    static String text(JsonNode node, String field, int max) {
        String s = raw(node, field);
        return s == null ? null : s.length() <= max ? s : s.substring(0, max).strip();
    }

    /** Trimmed text only if it fits in {@code max} characters (for sentences that must not be cut); else null. */
    static String textWithin(JsonNode node, String field, int max) {
        String s = raw(node, field);
        return s == null || s.length() > max ? null : s;
    }

    /** The enum constant with this name (case-insensitive), or null. */
    static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        String s = raw(node, field);
        if (s == null) return null;
        try {
            return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** A whole-number id that {@code exists} accepts, or null. */
    static Long id(JsonNode node, String field, Predicate<Long> exists) {
        Long id = wholeNumber(node, field);
        return id != null && exists.test(id) ? id : null;
    }

    /** A whole number in [min, max], or null. */
    static Integer integer(JsonNode node, String field, int min, int max) {
        Long n = wholeNumber(node, field);
        return n == null || n < min || n > max ? null : n.intValue();
    }

    /** True only for a JSON {@code true}. */
    static boolean bool(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v != null && v.isBoolean() && v.booleanValue();
    }

    /** An ISO local date-time ("2026-11-14T09:30:00", an offset is tolerated and dropped), to the second; else null. */
    static LocalDateTime dateTime(JsonNode node, String field) {
        String s = raw(node, field);
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME).truncatedTo(ChronoUnit.SECONDS);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** True when the field is present with a non-null value (used to notice values that were proposed but rejected). */
    static boolean present(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v != null && !v.isNull() && !(v.isTextual() && v.asText().isBlank());
    }

    private static String raw(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || !v.isTextual()) return null;
        String s = v.asText().strip();
        return s.isEmpty() ? null : s;
    }

    private static Long wholeNumber(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null) return null;
        if (v.isIntegralNumber() && v.canConvertToLong()) return v.longValue();
        if (v.isTextual() && v.asText().strip().matches("\\d{1,18}")) return Long.parseLong(v.asText().strip());
        return null;
    }
}
