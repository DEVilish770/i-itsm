package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Feature 5: a 3-4 sentence summary, for the IT manager, of the numbers {@link ReportService} already computes.
 * While the figures stay the same the last AI summary is reused, so reloading the dashboard costs no calls.
 * Rules fallback: a template filled in with the same numbers.
 */
@Service
public class ManagerSummaryService {

    static final int SUMMARY_MAX = 1200;
    static final String RATING = "averageFeedbackRating";

    private static final Map<String, Object> SCHEMA = OutputSchema.object(
            "summary", OutputSchema.string("Three or four plain sentences, no lists, no headings"));

    private final ReportService reports;
    private final AiModeService ai;
    private final ObjectMapper json;
    private final Clock clock;
    private final AtomicReference<Cached> lastAi = new AtomicReference<>();

    public ManagerSummaryService(ReportService reports, AiModeService ai, ObjectMapper json, Clock clock) {
        this.reports = reports;
        this.ai = ai;
        this.json = json;
        this.clock = clock;
    }

    /** Body of {@code GET /api/admin/reports/summary}. */
    public record Result(TextSource source, String text, OffsetDateTime generatedAt) { }

    private record Cached(Map<String, Object> figures, Result result) { }

    @PreAuthorize("hasRole('ADMIN')")
    public Result summarize() {
        Map<String, Object> figures = figures();
        if (!ai.isLive()) return rules(figures);
        Cached cached = lastAi.get();
        if (cached != null && cached.figures().equals(figures)) return cached.result();
        Optional<Result> fresh = aiSummary(figures);
        fresh.ifPresent(r -> lastAi.set(new Cached(figures, r)));
        return fresh.orElseGet(() -> rules(figures));
    }

    /**
     * The report numbers, grouped as the reports endpoints return them, except that the average rating is left out
     * while nobody has rated a fix, so neither the template nor Claude can report that 0 as a real (very bad) score.
     */
    Map<String, Object> figures() {
        Map<String, Object> performance = reports.performance();
        if (rating(performance) == null && performance.containsKey(RATING)) {
            performance = new LinkedHashMap<>(performance);
            performance.remove(RATING);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("overview", reports.overview());
        f.put("performance", performance);
        f.put("busiestServices", reports.serviceAnalysis().stream().limit(3).toList());
        f.put("topCategories", reports.categoryAnalysis().stream().limit(3).toList());
        f.put("technicianWorkload", reports.technicianWorkload());
        return f;
    }

    private Optional<Result> aiSummary(Map<String, Object> figures) {
        String system = """
                Write a summary of the service-desk figures below for an IT manager: three or four plain sentences. Lead \
                with the most important numbers, point out anything that stands out (many missed deadlines, a busy \
                service, a technician with a heavy load) and say nothing that is not in the figures. No headings, lists \
                or markdown.
                """ + Prompts.DATA_NOT_INSTRUCTIONS;
        String data;
        try {
            data = "Figures (JSON):\n" + json.writeValueAsString(figures);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
        return ai.call(new AiRequest(AiFeature.MANAGER_SUMMARY, system, List.of(AiMessage.user(data)), SCHEMA))
                .map(answer -> ModelOutput.textWithin(answer, "summary", SUMMARY_MAX))
                .map(text -> new Result(TextSource.AI, text, now()));
    }

    /** RULES text: the same numbers in a fixed set of sentences (no rating sentence while nobody has rated). */
    Result rules(Map<String, Object> figures) {
        Map<?, ?> o = (Map<?, ?>) figures.get("overview");
        Map<?, ?> p = (Map<?, ?>) figures.get("performance");
        Number rating = rating(p);
        long total = number(o, "totalIncidents");
        String text;
        if (total == 0) {
            text = "No incidents have been reported yet, so there is nothing to summarise. "
                    + "Deadlines and response times are tracked from the first report. "
                    + "The figures will appear here once employees start reporting issues.";
        } else {
            long open = number(o, "openIncidents"), resolved = number(o, "resolvedIncidents"), atRisk = number(o, "slaAtRisk");
            text = "There " + (total == 1 ? "is 1 incident" : "are " + total + " incidents") + " in total: "
                    + open + (open == 1 ? " is" : " are") + " still open (" + number(o, "criticalIncidents") + " of them critical) and "
                    + resolved + (resolved == 1 ? " has" : " have") + " been resolved. "
                    + count(number(o, "slaViolations"), "incident has", "incidents have") + " missed the SLA deadline and "
                    + atRisk + (atRisk == 1 ? " is" : " are") + " at risk of missing it. "
                    + (number(p, "resolvedCount") == 0
                    ? "No incident has been resolved yet, so there are no response or fix times to report."
                    : "On average an incident is acknowledged after " + count(number(p, "meanTimeToAcknowledgeMinutes"), "minute", "minutes")
                    + " and resolved after " + count(number(p, "meanTimeToResolveMinutes"), "minute", "minutes") + ". "
                    + "SLA compliance is " + p.get("slaCompliancePct") + "%"
                    + (rating == null ? "." : " and the average feedback rating is " + rating + " out of 5."));
        }
        return new Result(TextSource.RULES, text, now());
    }

    /** The average rating, or null while nobody has rated a fix: ratings run from 1 to 5, so 0 can only mean "none yet". */
    private static Number rating(Map<?, ?> performance) {
        return performance != null && performance.get(RATING) instanceof Number n && n.doubleValue() > 0 ? n : null;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), AiModeService.IST).truncatedTo(ChronoUnit.SECONDS);
    }

    private static long number(Map<?, ?> m, String key) {
        return m != null && m.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static String count(long n, String one, String many) { return n + " " + (n == 1 ? one : many); }
}
