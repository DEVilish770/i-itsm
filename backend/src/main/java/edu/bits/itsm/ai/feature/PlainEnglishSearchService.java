package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.domain.sla.SLAStatus;
import edu.bits.itsm.exception.InvalidAiRequestException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Feature 3: plain-English search. Claude turns a sentence ("urgent VPN problems from last week") into the existing
 * search filters only; the frontend then calls the normal {@code GET /api/incidents}, so visibility rules still apply.
 * Rules fallback: the sentence itself as the free-text filter {@code q}.
 */
@Service
public class PlainEnglishSearchService {

    static final int QUERY_MAX = 300;
    static final int EXPLANATION_MAX = 300;
    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private static final Map<String, Object> SCHEMA = OutputSchema.object(
            "q", OutputSchema.nullable(OutputSchema.string("Free text matched against incident number, title and description")),
            "status", OutputSchema.nullable(OutputSchema.enumOf(IncidentStatus.class, "Lifecycle status")),
            "priority", OutputSchema.nullable(OutputSchema.enumOf(Priority.class, "P1 critical, P2 high, P3 medium, P4 low")),
            "categoryId", OutputSchema.nullable(OutputSchema.integer("Category id from the catalog")),
            "serviceId", OutputSchema.nullable(OutputSchema.integer("Service id from the catalog")),
            "technicianId", OutputSchema.nullable(OutputSchema.integer("Technician id from the catalog")),
            "slaStatus", OutputSchema.nullable(OutputSchema.enumOf(SLAStatus.class, "Deadline health")),
            "from", OutputSchema.nullable(OutputSchema.string("Created at or after, ISO local date-time yyyy-MM-ddTHH:mm:ss")),
            "to", OutputSchema.nullable(OutputSchema.string("Created at or before, ISO local date-time yyyy-MM-ddTHH:mm:ss")),
            "explanation", OutputSchema.string("One plain sentence describing the filters you chose"));

    private final AiModeService ai;
    private final LiveCatalog catalog;
    private final Clock clock;

    public PlainEnglishSearchService(AiModeService ai, LiveCatalog catalog, Clock clock) {
        this.ai = ai;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Body of {@code POST /api/ai/search}. */
    public record Result(TextSource source, SearchFilters filters, String explanation) { }

    /** @throws InvalidAiRequestException for an empty or over-long query, in every mode */
    public Result translate(String query) {
        if (query == null || query.isBlank() || query.strip().length() > QUERY_MAX)
            throw new InvalidAiRequestException("query must be 1-" + QUERY_MAX + " characters");
        String q = query.strip();
        if (!ai.isLive()) return rules(q);
        LiveCatalog.Snapshot live = catalog.snapshot();
        return ai.call(request(q, live)).flatMap(answer -> read(answer, live)).orElseGet(() -> rules(q));
    }

    static Result rules(String query) {
        return new Result(TextSource.RULES, SearchFilters.textOnly(query),
                "Showing incidents whose number, title or description contains \"" + query + "\".");
    }

    private AiRequest request(String query, LiveCatalog.Snapshot live) {
        String now = LocalDateTime.ofInstant(clock.instant(), AiModeService.IST).truncatedTo(ChronoUnit.MINUTES).format(NOW);
        String system = """
                Turn the user's sentence into search filters for the I-ITSM incident list. Use null for every filter the \
                sentence does not ask for; never guess.
                - status: NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO (waiting for the reporter), \
                RESOLVED, VERIFIED, CLOSED.
                - priority: P1 = critical or "urgent", P2 = high, P3 = medium, P4 = low.
                - slaStatus: VIOLATED = deadline missed or overdue, AT_RISK = about to miss it, ON_TRACK, MET, NOT_APPLICABLE.
                - from / to: creation-time range as local date-times in India time (Asia/Kolkata). It is now %s.
                - q: free words to match in the incident number, title or description, only for words no other filter covers.
                - Ids only from the catalog below.
                """.formatted(now)
                + Prompts.DATA_NOT_INSTRUCTIONS + "\n\n" + live.servicesAndCategories() + "\n\n" + live.technicianList();
        return new AiRequest(AiFeature.SEARCH, system, List.of(AiMessage.user(query)), SCHEMA);
    }

    /** Uses Claude's explanation only when every filter it proposed survived validation. */
    private static Optional<Result> read(JsonNode answer, LiveCatalog.Snapshot live) {
        SearchFilters filters = SearchFilters.fromModel(answer, live);
        if (filters.isEmpty() && !filters.rejected().isEmpty()) return Optional.empty();   // nothing valid was proposed
        String explanation = filters.rejected().isEmpty() ? ModelOutput.text(answer, "explanation", EXPLANATION_MAX) : null;
        return Optional.of(new Result(TextSource.AI, filters, explanation != null ? explanation : filters.describe(live)));
    }
}
