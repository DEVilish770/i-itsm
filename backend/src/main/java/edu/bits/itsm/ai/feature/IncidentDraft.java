package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.domain.incident.Impact;
import edu.bits.itsm.domain.incident.Urgency;

import java.util.List;
import java.util.Map;

/**
 * A proposed incident with the same field names as the create-incident request
 * ({@link edu.bits.itsm.web.dto.IncidentRequests.CreateIncident}), minus its form switches. Any field may be null.
 * It is built only from validated model output and is never saved: the employee reviews it and submits the normal form,
 * which runs the usual service-layer checks.
 */
public record IncidentDraft(String title, String description, Long serviceId, Long categoryId,
                            Impact impact, Urgency urgency, Integer affectedUsers) {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 4000;
    static final int AFFECTED_USERS_MAX = 1_000_000;
    private static final int TITLE_FROM_CHAT = 80;

    /** Validates a model's draft: unknown ids, bad enum values and out-of-range numbers become null. */
    static IncidentDraft fromModel(JsonNode node, LiveCatalog.Snapshot catalog) {
        if (node == null || !node.isObject()) return new IncidentDraft(null, null, null, null, null, null, null);
        return new IncidentDraft(
                ModelOutput.text(node, "title", TITLE_MAX),
                ModelOutput.text(node, "description", DESCRIPTION_MAX),
                ModelOutput.id(node, "serviceId", catalog::hasService),
                ModelOutput.id(node, "categoryId", catalog::hasCategory),
                ModelOutput.enumValue(node, "impact", Impact.class),
                ModelOutput.enumValue(node, "urgency", Urgency.class),
                ModelOutput.integer(node, "affectedUsers", 0, AFFECTED_USERS_MAX));
    }

    /** Rules part of the chat: a missing title or description is filled from the employee's own words. */
    IncidentDraft withTextFrom(List<String> userMessages) {
        if (userMessages.isEmpty() || (title != null && description != null)) return this;
        String joined = String.join("\n", userMessages);
        String t = title != null ? title : shorten(userMessages.get(0).strip(), TITLE_FROM_CHAT);
        String d = description != null ? description : shorten(joined.strip(), DESCRIPTION_MAX);
        return new IncidentDraft(t, d, serviceId, categoryId, impact, urgency, affectedUsers);
    }

    /** The structured-output schema of a draft; enum values come from the domain enums, ids are checked afterwards. */
    static Map<String, Object> schema() {
        return OutputSchema.object(
                "title", OutputSchema.nullable(OutputSchema.string("Short title, at most 80 characters")),
                "description", OutputSchema.nullable(OutputSchema.string("What fails, in 1-3 plain sentences, with any error code or message")),
                "serviceId", OutputSchema.nullable(OutputSchema.integer("Id of the affected service from the catalog, or null")),
                "categoryId", OutputSchema.nullable(OutputSchema.integer("Id of the best category from the catalog, or null")),
                "impact", OutputSchema.nullable(OutputSchema.enumOf(Impact.class, "How widely people are affected")),
                "urgency", OutputSchema.nullable(OutputSchema.enumOf(Urgency.class, "How soon it must be fixed")),
                "affectedUsers", OutputSchema.nullable(OutputSchema.integer("Roughly how many people are affected, or null")));
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max - 3);
        return s.substring(0, cut > max / 2 ? cut : max - 3).strip() + "...";
    }
}
