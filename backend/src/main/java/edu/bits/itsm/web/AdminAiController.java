package edu.bits.itsm.web;

import edu.bits.itsm.ai.AiModeService;
import edu.bits.itsm.ai.AiStatus;
import edu.bits.itsm.ai.feature.ManagerSummaryService;
import edu.bits.itsm.web.dto.AiRequests.AiSwitch;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Admin-only AI endpoints (under /api/admin, so ROLE_ADMIN via SecurityConfig, and @PreAuthorize on the services). */
@RestController
@RequestMapping("/api/admin")
public class AdminAiController {

    private final AiModeService mode;
    private final ManagerSummaryService summaries;

    public AdminAiController(AiModeService mode, ManagerSummaryService summaries) {
        this.mode = mode;
        this.summaries = summaries;
    }

    /** Runtime on/off switch for every AI feature; not persisted (a restart goes back to itsm.ai.enabled). */
    @PutMapping("/ai")
    public AiStatus switchAi(@Valid @RequestBody AiSwitch r) { return mode.setEnabled(r.enabled()); }

    @GetMapping("/reports/summary")
    public ManagerSummaryService.Result summary() { return summaries.summarize(); }
}
