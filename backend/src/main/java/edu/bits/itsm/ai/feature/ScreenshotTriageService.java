package edu.bits.itsm.ai.feature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.gateway.AiMessage;
import edu.bits.itsm.ai.gateway.AiRequest;
import edu.bits.itsm.domain.notification.TextSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Feature 1: screenshot to ticket. Claude reads an error screenshot and proposes a draft incident; nothing is saved.
 * Rules fallback: no draft, a sentence asking the employee to fill in the form.
 */
@Service
public class ScreenshotTriageService {

    static final String RULES_MESSAGE = "Reading screenshots with AI is not available right now, so please describe the problem in the form yourself.";
    static final String DEFAULT_SUMMARY = "Draft made from your screenshot.";

    private static final Map<String, Object> SCHEMA = OutputSchema.object(
            "summary", OutputSchema.string("One short line saying what the screenshot shows"),
            "draft", IncidentDraft.schema());

    private final AiModeService ai;
    private final LiveCatalog catalog;

    public ScreenshotTriageService(AiModeService ai, LiveCatalog catalog) {
        this.ai = ai;
        this.catalog = catalog;
    }

    /** Body of {@code POST /api/ai/screenshot-triage}: AI = available + draft + summary; RULES = message only. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(TextSource source, boolean available, IncidentDraft draft, String summary, String message) {
        static Result rules() { return new Result(TextSource.RULES, false, null, null, RULES_MESSAGE); }
    }

    /** @throws edu.bits.itsm.exception.InvalidAiRequestException for a bad image, in every mode */
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public Result triage(String imageBase64, String mediaType) {
        Screenshot shot = Screenshot.of(imageBase64, mediaType);
        if (!ai.isLive()) return Result.rules();
        LiveCatalog.Snapshot live = catalog.snapshot();
        return ai.call(request(shot, live)).map(answer -> read(answer, live)).orElseGet(Result::rules);
    }

    private static AiRequest request(Screenshot shot, LiveCatalog.Snapshot live) {
        String system = """
                You help employees report IT problems to the I-ITSM service desk. Read the error screenshot and propose a \
                draft incident.
                - title: short and specific, for example "Payment page shows error 500".
                - description: what fails in one to three plain sentences, including any error code or message you can read.
                - serviceId and categoryId: only ids from the catalog below; use null when nothing fits or you are unsure.
                - impact: how widely people are affected (LOW = one person, CRITICAL = the whole company).
                - urgency: how soon it must be fixed (LOW = can wait, CRITICAL = work has stopped).
                - affectedUsers: only if the screenshot shows it, else null.
                - summary: one short line saying what the screenshot shows. If it is not an error screenshot, say so and \
                leave the draft fields null.
                """ + Prompts.DATA_NOT_INSTRUCTIONS + "\n" + Prompts.PROPOSE_ONLY + "\n\n" + live.servicesAndCategories();
        return new AiRequest(AiFeature.SCREENSHOT_TRIAGE, system,
                List.of(AiMessage.userWithImage(shot.asImage(), "Here is the screenshot of the error. Propose the draft incident.")),
                SCHEMA);
    }

    private static Result read(JsonNode answer, LiveCatalog.Snapshot live) {
        IncidentDraft draft = IncidentDraft.fromModel(answer.get("draft"), live);
        String summary = ModelOutput.text(answer, "summary", 200);
        return new Result(TextSource.AI, true, draft, summary == null ? DEFAULT_SUMMARY : summary, null);
    }
}
