package edu.bits.itsm.ai.feature;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON schemas sent as structured-output formats, within what the API supports: every object is closed
 * ({@code additionalProperties: false}) and lists all its fields as required; "may be null" is {@code anyOf} with null.
 * Maps keep insertion order, so a schema serialises to the same bytes every time (the API caches compiled schemas).
 */
final class OutputSchema {

    private OutputSchema() { }

    /** An object whose fields are given as name, schema, name, schema, ... */
    static Map<String, Object> object(Object... nameSchemaPairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < nameSchemaPairs.length; i += 2) properties.put((String) nameSchemaPairs[i], nameSchemaPairs[i + 1]);
        return map("type", "object", "properties", Collections.unmodifiableMap(properties),
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }

    static Map<String, Object> string(String description) { return map("type", "string", "description", description); }

    static Map<String, Object> integer(String description) { return map("type", "integer", "description", description); }

    static Map<String, Object> bool(String description) { return map("type", "boolean", "description", description); }

    /** A string restricted to the constant names of an enum. */
    static Map<String, Object> enumOf(Class<? extends Enum<?>> type, String description) {
        List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return map("type", "string", "enum", names, "description", description);
    }

    static Map<String, Object> nullable(Map<String, Object> schema) {
        return map("anyOf", List.of(schema, map("type", "null")));
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return Collections.unmodifiableMap(m);
    }
}
