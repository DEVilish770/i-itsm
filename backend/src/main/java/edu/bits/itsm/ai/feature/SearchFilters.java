package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Filters for {@code GET /api/incidents}, restricted to the query parameters that search accepts ({@link #KEYS}, the
 * components of {@link edu.bits.itsm.web.dto.IncidentRequests.SearchCriteria}). Every value is validated: enums must
 * parse, ids must exist in the live catalog, dates must be ISO date-times with {@code from <= to}. Anything else is
 * dropped. Serialises as a flat JSON object that holds only the filters in use.
 */
public final class SearchFilters {

    public static final List<String> KEYS = List.of("q", "status", "priority", "categoryId", "serviceId", "technicianId", "slaStatus", "from", "to");
    static final int Q_MAX = 200;

    private final Map<String, Object> values;
    private final List<String> rejected;

    private SearchFilters(Map<String, Object> values, List<String> rejected) {
        this.values = Collections.unmodifiableMap(values);
        this.rejected = List.copyOf(rejected);
    }

    /** Rules fallback: the whole sentence as the free-text filter. */
    public static SearchFilters textOnly(String query) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("q", query.strip());
        return new SearchFilters(v, List.of());
    }

    /** Keeps the valid whitelisted filters of a model answer and remembers which proposed ones were rejected. */
    static SearchFilters fromModel(JsonNode answer, LiveCatalog.Snapshot catalog) {
        Map<String, Object> v = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        keep(v, rejected, answer, "q", ModelOutput.text(answer, "q", Q_MAX));
        keep(v, rejected, answer, "status", name(ModelOutput.enumValue(answer, "status", IncidentStatus.class)));
        keep(v, rejected, answer, "priority", name(ModelOutput.enumValue(answer, "priority", Priority.class)));
        keep(v, rejected, answer, "categoryId", ModelOutput.id(answer, "categoryId", catalog::hasCategory));
        keep(v, rejected, answer, "serviceId", ModelOutput.id(answer, "serviceId", catalog::hasService));
        keep(v, rejected, answer, "technicianId", ModelOutput.id(answer, "technicianId", catalog::hasTechnician));
        keep(v, rejected, answer, "slaStatus", name(ModelOutput.enumValue(answer, "slaStatus", SLAStatus.class)));
        LocalDateTime from = ModelOutput.dateTime(answer, "from");
        LocalDateTime to = ModelOutput.dateTime(answer, "to");
        if (from != null && to != null && from.isAfter(to)) { from = null; to = null; }   // an impossible range: drop both
        keep(v, rejected, answer, "from", iso(from));
        keep(v, rejected, answer, "to", iso(to));
        return new SearchFilters(v, rejected);
    }

    @JsonValue
    public Map<String, Object> asMap() { return values; }

    public boolean isEmpty() { return values.isEmpty(); }

    /** Filters the model proposed that failed validation. */
    public List<String> rejected() { return rejected; }

    /** A plain sentence describing the filters, written from the validated values (so it never mentions a dropped one). */
    String describe(LiveCatalog.Snapshot catalog) {
        if (values.isEmpty()) return "Showing all incidents you can see.";
        List<String> parts = new ArrayList<>();
        if (values.containsKey("priority")) parts.add("priority " + values.get("priority") + " (" + Priority.valueOf((String) values.get("priority")).getLabel().toLowerCase(Locale.ROOT) + ")");
        if (values.containsKey("status")) parts.add("status " + words(values.get("status")));
        if (values.containsKey("slaStatus")) parts.add("SLA " + words(values.get("slaStatus")));
        if (values.containsKey("serviceId")) parts.add("on " + catalog.serviceName((Long) values.get("serviceId")));
        if (values.containsKey("categoryId")) parts.add("in category " + catalog.categoryName((Long) values.get("categoryId")));
        if (values.containsKey("technicianId")) parts.add("assigned to " + catalog.technicianName((Long) values.get("technicianId")));
        if (values.containsKey("from")) parts.add("created from " + values.get("from"));
        if (values.containsKey("to")) parts.add("created until " + values.get("to"));
        if (values.containsKey("q")) parts.add("mentioning \"" + values.get("q") + "\"");
        return "Showing incidents with " + String.join(", ", parts) + ".";
    }

    private static void keep(Map<String, Object> values, List<String> rejected, JsonNode answer, String key, Object value) {
        if (value != null) values.put(key, value);
        else if (ModelOutput.present(answer, key)) rejected.add(key);
    }

    private static String name(Enum<?> e) { return e == null ? null : e.name(); }

    private static String iso(LocalDateTime t) { return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }

    private static String words(Object enumName) { return enumName.toString().toLowerCase(Locale.ROOT).replace('_', ' '); }

    @Override public boolean equals(Object o) { return o instanceof SearchFilters f && values.equals(f.values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return values.toString(); }
}
