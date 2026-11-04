package edu.bits.itsm.web;

import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;
import edu.bits.itsm.intelligence.CategorySuggestionService;
import edu.bits.itsm.intelligence.DuplicateDetectionService;
import edu.bits.itsm.intelligence.ResolutionSuggestionService;
import edu.bits.itsm.repository.AuditRecordRepository;
import edu.bits.itsm.service.AssignmentService;
import edu.bits.itsm.service.IncidentService;
import edu.bits.itsm.web.dto.IncidentRequests.*;
import edu.bits.itsm.web.dto.Views.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Incident lifecycle endpoints. Role checks: URL-level in SecurityConfig + ownership checks in IncidentService. */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidents;
    private final AssignmentService assignment;
    private final AuditRecordRepository audit;
    private final DuplicateDetectionService duplicates;
    private final CategorySuggestionService categories;
    private final ResolutionSuggestionService resolutions;

    public IncidentController(IncidentService incidents, AssignmentService assignment, AuditRecordRepository audit,
                              DuplicateDetectionService duplicates, CategorySuggestionService categories, ResolutionSuggestionService resolutions) {
        this.incidents = incidents; this.assignment = assignment; this.audit = audit;
        this.duplicates = duplicates; this.categories = categories; this.resolutions = resolutions;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public IncidentDetail create(@Valid @RequestBody CreateIncident req) { return incidents.report(req); }

    @GetMapping("/{id}")
    public IncidentDetail get(@PathVariable Long id) { return incidents.getDetail(id); }

    @GetMapping("/mine")
    public List<IncidentSummary> mine() { return incidents.myReported().stream().map(IncidentSummary::of).toList(); }

    @GetMapping("/queue")
    public List<IncidentSummary> queue() { return incidents.myQueue().stream().map(IncidentSummary::of).toList(); }

    /** Search & filter: ≥ 5 criteria (text/number, status, priority, category, service, technician, SLA status, date range). */
    @GetMapping
    public Page<IncidentSummary> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) IncidentStatus status,
                                        @RequestParam(required = false) Priority priority,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) Long serviceId,
                                        @RequestParam(required = false) Long technicianId,
                                        @RequestParam(required = false) SLAStatus slaStatus,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] s = sort.split(",");
        Sort sortSpec = Sort.by(s.length > 1 && s[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, s[0]);
        return incidents.search(new SearchCriteria(q, status, priority, categoryId, serviceId, technicianId, slaStatus, from, to),
                PageRequest.of(page, Math.min(size, 100), sortSpec)).map(IncidentSummary::of);
    }

    @GetMapping("/{id}/history")
    public List<AuditView> history(@PathVariable Long id) {
        incidents.get(id); // authorisation check
        return audit.findByIncidentIdOrderByTimestampAsc(id).stream().map(AuditView::of).toList();
    }

    // ---- lifecycle actions
    @PutMapping("/{id}")                 public IncidentDetail update(@PathVariable Long id, @Valid @RequestBody UpdateIncident r) { return incidents.updateDetails(id, r); }
    @PostMapping("/{id}/classify")       public IncidentDetail classify(@PathVariable Long id, @Valid @RequestBody Reclassify r) { return incidents.reclassify(id, r.categoryId(), r.priority(), r.reason()); }
    @PostMapping("/{id}/assign")         public IncidentDetail assign(@PathVariable Long id, @Valid @RequestBody ManualAssign r) { return incidents.assignManually(id, r.technicianId(), r.reason()); }
    @PostMapping("/{id}/auto-assign")    public IncidentDetail autoAssign(@PathVariable Long id, @RequestBody(required = false) AutoAssign r) { return incidents.autoAssign(id, r == null ? null : r.strategy()); }
    @PostMapping("/{id}/acknowledge")    public IncidentDetail acknowledge(@PathVariable Long id) { return incidents.acknowledge(id); }
    @PostMapping("/{id}/start")          public IncidentDetail start(@PathVariable Long id) { return incidents.startInvestigation(id); }
    @PostMapping("/{id}/notes")          public IncidentDetail note(@PathVariable Long id, @Valid @RequestBody Text r) { return incidents.addNote(id, r.text()); }
    @PostMapping("/{id}/request-info")   public IncidentDetail requestInfo(@PathVariable Long id, @Valid @RequestBody Text r) { return incidents.requestInfo(id, r.text()); }
    @PostMapping("/{id}/provide-info")   public IncidentDetail provideInfo(@PathVariable Long id, @Valid @RequestBody Text r) { return incidents.provideInfo(id, r.text()); }
    @PostMapping("/{id}/resolve")        public IncidentDetail resolve(@PathVariable Long id, @Valid @RequestBody Resolve r) { return incidents.resolve(id, r.summary(), r.rootCause()); }
    @PostMapping("/{id}/verify")         public IncidentDetail verify(@PathVariable Long id) { return incidents.verify(id); }
    @PostMapping("/{id}/reopen")         public IncidentDetail reopen(@PathVariable Long id, @Valid @RequestBody Text r) { return incidents.reopen(id, r.text()); }
    @PostMapping("/{id}/close")          public IncidentDetail close(@PathVariable Long id, @RequestBody(required = false) Text r) { return incidents.close(id, r == null ? null : r.text()); }
    @PostMapping("/{id}/feedback")       public IncidentDetail feedback(@PathVariable Long id, @Valid @RequestBody FeedbackReq r) { return incidents.feedback(id, r.rating(), r.comment()); }

    // ---- intelligence
    @GetMapping("/{id}/recommendations")
    public List<Map<String, Object>> recommendations(@PathVariable Long id) {
        return assignment.recommend(incidents.get(id)).stream().map(r -> Map.<String, Object>of(
                "technicianId", r.technician().getId(), "name", r.technician().getFullName(), "score", Math.round(r.score() * 10) / 10.0,
                "workload", r.workload(), "maxWorkload", r.technician().getMaxWorkload(), "skillMatch", r.skillMatch(),
                "experienceYears", r.technician().getExperienceYears())).toList();
    }

    @GetMapping("/{id}/similar")
    public List<Map<String, Object>> similar(@PathVariable Long id) {
        var i = incidents.get(id);
        return duplicates.findSimilar(i.getService().getId(), i.getTitle(), i.getDescription(), i.getId()).stream()
                .map(m -> Map.<String, Object>of("id", m.incident().getId(), "incidentNumber", m.incident().getIncidentNumber(),
                        "title", m.incident().getTitle(), "status", m.incident().getStatus().name(),
                        "similarity", Math.round(m.similarity() * 100), "likelyDuplicate", m.likelyDuplicate())).toList();
    }

    @GetMapping("/{id}/suggested-resolutions")
    public List<ResolutionSuggestionService.Suggestion> suggestedResolutions(@PathVariable Long id) { return resolutions.suggestFor(incidents.get(id)); }

    /** Pre-submission helper for the "new incident" form: category suggestion + duplicate warning. */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, Object> body) {
        String title = String.valueOf(body.getOrDefault("title", ""));
        String desc = String.valueOf(body.getOrDefault("description", ""));
        Object sid = body.get("serviceId");
        List<Map<String, Object>> cats = categories.suggest(title, desc).stream().limit(3).map(s -> Map.<String, Object>of(
                "categoryId", s.category().getId(), "name", s.category().getName(), "confidence", Math.round(s.confidence() * 100), "keywords", s.matchedKeywords())).toList();
        List<Map<String, Object>> dups = sid == null ? List.of() : duplicates.findSimilar(Long.valueOf(sid.toString()), title, desc, null).stream()
                .filter(m -> m.similarity() > 0.15).limit(5)
                .map(m -> Map.<String, Object>of("id", m.incident().getId(), "incidentNumber", m.incident().getIncidentNumber(), "title", m.incident().getTitle(),
                        "status", m.incident().getStatus().name(), "similarity", Math.round(m.similarity() * 100), "likelyDuplicate", m.likelyDuplicate())).toList();
        return Map.of("categorySuggestions", cats, "similarIncidents", dups);
    }
}
