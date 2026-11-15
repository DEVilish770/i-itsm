package edu.bits.itsm.web;

import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.AiStatus;
import edu.bits.itsm.ai.feature.ChatReportingService;
import edu.bits.itsm.ai.feature.PlainEnglishSearchService;
import edu.bits.itsm.ai.feature.ScreenshotTriageService;
import edu.bits.itsm.web.dto.AiRequests.Chat;
import edu.bits.itsm.web.dto.AiRequests.ScreenshotTriage;
import edu.bits.itsm.web.dto.AiRequests.Search;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Claude-powered helpers. They only propose: a draft or a set of filters comes back, nothing is saved here. The UI
 * shows the proposal and, when the employee confirms, calls the normal endpoints (POST /api/incidents, GET /api/incidents).
 * Roles: status and search for any logged-in user; screenshot triage and chat for employees and admins (SecurityConfig).
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiModeService mode;
    private final ScreenshotTriageService triage;
    private final ChatReportingService chat;
    private final PlainEnglishSearchService search;

    public AiController(AiModeService mode, ScreenshotTriageService triage, ChatReportingService chat, PlainEnglishSearchService search) {
        this.mode = mode;
        this.triage = triage;
        this.chat = chat;
        this.search = search;
    }

    @GetMapping("/status")
    public AiStatus status() { return mode.status(); }

    @PostMapping("/screenshot-triage")
    public ScreenshotTriageService.Result screenshotTriage(@Valid @RequestBody ScreenshotTriage r) {
        return triage.triage(r.imageBase64(), r.mediaType());
    }

    @PostMapping("/chat")
    public ChatReportingService.Result chat(@Valid @RequestBody Chat r) { return chat.reply(r.messages()); }

    @PostMapping("/search")
    public PlainEnglishSearchService.Result search(@Valid @RequestBody Search r) { return search.translate(r.query()); }
}
