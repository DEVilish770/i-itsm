package edu.bits.itsm.service;

import edu.bits.itsm.config.StrategyRegistry;
import edu.bits.itsm.domain.incident.*;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.strategy.priority.PriorityContext;
import edu.bits.itsm.domain.strategy.priority.PriorityResult;
import edu.bits.itsm.domain.user.Role;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentEventBus;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.exception.*;
import edu.bits.itsm.intelligence.CategorySuggestionService;
import edu.bits.itsm.intelligence.DuplicateDetectionService;
import edu.bits.itsm.repository.*;
import edu.bits.itsm.security.CurrentUser;
import edu.bits.itsm.web.dto.IncidentRequests.*;
import edu.bits.itsm.web.dto.Views.IncidentDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static edu.bits.itsm.repository.IncidentSpecifications.*;

/**
 * Application service orchestrating the incident lifecycle:
 * Report → Classify → Prioritize → Assign → Investigate → Resolve → Verify → Close.
 * <p>
 * It composes the domain model, the strategies, the SLA engine, the intelligence services and the
 * event bus. All authorization checks that depend on *which* incident (ownership) live here.
 */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidents;
    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final TechnicianRepository technicians;
    private final StrategyRegistry strategies;
    private final SLAService sla;
    private final AssignmentService assignment;
    private final DuplicateDetectionService duplicates;
    private final CategorySuggestionService categorySuggestion;
    private final IncidentEventBus bus;
    private final IncidentNumberGenerator numbers;
    private final CurrentUser currentUser;

    public IncidentService(IncidentRepository incidents, ITServiceRepository services, IncidentCategoryRepository categories,
                           TechnicianRepository technicians, StrategyRegistry strategies, SLAService sla,
                           AssignmentService assignment, DuplicateDetectionService duplicates,
                           CategorySuggestionService categorySuggestion, IncidentEventBus bus,
                           IncidentNumberGenerator numbers, CurrentUser currentUser) {
        this.incidents = incidents; this.services = services; this.categories = categories;
        this.technicians = technicians; this.strategies = strategies; this.sla = sla; this.assignment = assignment;
        this.duplicates = duplicates; this.categorySuggestion = categorySuggestion; this.bus = bus;
        this.numbers = numbers; this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Incident get(Long id) {
        Incident i = incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
        assertCanView(i, currentUser.get());
        return i;
    }

    @Transactional(readOnly = true)
    public IncidentDetail getDetail(Long id) { return IncidentDetail.of(get(id)); }

    @Transactional(readOnly = true)
    public Page<Incident> search(SearchCriteria c, Pageable pageable) {
        User me = currentUser.get();
        Specification<Incident> spec = Specification.where(hasNumberOrText(c.q()))
                .and(hasStatus(c.status())).and(hasPriority(c.priority()))
                .and(hasCategory(c.categoryId())).and(hasService(c.serviceId()))
                .and(hasTechnician(c.technicianId())).and(hasSlaStatus(c.slaStatus()))
                .and(createdAfter(c.from())).and(createdBefore(c.to()));
        // Employees only ever see their own incidents; technicians see their own queue unless admin.
        if (me.getRole() == Role.EMPLOYEE) spec = spec.and(hasReporter(me.getId()));
        else if (me.getRole() == Role.TECHNICIAN && c.technicianId() == null) spec = spec.and(hasTechnician(me.getId()));
        return incidents.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public List<Incident> myReported() { return incidents.findByReporterIdOrderByCreatedAtDesc(currentUser.get().getId()); }

    @Transactional(readOnly = true)
    public List<Incident> myQueue() {
        List<IncidentStatus> active = List.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
                IncidentStatus.IN_PROGRESS, IncidentStatus.PENDING_INFO, IncidentStatus.RESOLVED);
        return incidents.findByAssignedTechnicianIdAndStatusInOrderByPriorityAscCreatedAtAsc(currentUser.get().getId(), active);
    }

    // ------------------------------------------------------------------ 1-6: report, classify, prioritise, SLA

    /**
     * Creates an incident: duplicate check → persist → classify (category) → prioritise (strategy)
     * → apply SLA → auto-assign (if enabled). Everything in one transaction.
     */
    public IncidentDetail report(CreateIncident req) {
        User reporter = currentUser.get();
        ITService service = services.findById(req.serviceId()).orElseThrow(() -> new ResourceNotFoundException("Service", req.serviceId()));
        if (!service.isActive()) throw new InvalidIncidentException("Service " + service.getName() + " is retired");

        // Intelligent feature: duplicate detection (soft block, override with forceCreate)
        var similar = duplicates.findSimilar(service.getId(), req.title(), req.description(), null);
        var likely = similar.stream().filter(DuplicateDetectionService.Match::likelyDuplicate).toList();
        if (!likely.isEmpty() && !Boolean.TRUE.equals(req.forceCreate()))
            throw new DuplicateIncidentException(likely.stream().map(m -> m.incident().getIncidentNumber()).toList());

        Incident incident = new Incident(req.title(), req.description(), reporter, service, req.impact(), req.urgency(),
                req.affectedUsers() == null ? 0 : req.affectedUsers());
        incident = incidents.saveAndFlush(incident);
        incident.assignNumber(numbers.format(incident.getId()));

        // Classify: explicit category wins, else rule-based suggestion, else "OTHER"
        IncidentCategory category = req.categoryId() != null
                ? categories.findById(req.categoryId()).orElseThrow(() -> new ResourceNotFoundException("Category", req.categoryId()))
                : categorySuggestion.best(req.title(), req.description())
                      .orElseGet(() -> categories.findByName("OTHER").orElseThrow(() -> new ResourceNotFoundException("Category", "OTHER")));

        PriorityResult pr = strategies.priorityStrategy().calculate(new PriorityContext(
                incident.getImpact(), incident.getUrgency(), service.getCriticality(), incident.getAffectedUsers(), service.getUserBase()));
        incident.classify(category, pr.priority(), pr.score(), pr.rationale());
        sla.apply(incident, incident.getCreatedAt());

        if (!likely.isEmpty()) incident.markRelatedTo(likely.get(0).incident());

        bus.publish(IncidentEvent.of(IncidentEventType.CREATED, incident, reporter.getUsername(), IncidentStatus.NEW,
                "Reported against " + service.getName()));
        bus.publish(IncidentEvent.of(IncidentEventType.CLASSIFIED, incident, "system",
                "Category " + category.getName() + "; " + pr.rationale()));
        if (!likely.isEmpty())
            bus.publish(IncidentEvent.of(IncidentEventType.DUPLICATE_DETECTED, incident, "system",
                    "Possibly duplicates " + likely.get(0).incident().getIncidentNumber()
                            + String.format(" (similarity %.0f%%)", likely.get(0).similarity() * 100)));

        if (Boolean.TRUE.equals(req.autoAssign())) {
            try { autoAssignInternal(incident, null, "system"); }
            catch (TechnicianUnavailableException ex) {
                bus.publish(IncidentEvent.of(IncidentEventType.NOTE_ADDED, incident, "system", "Auto-assignment deferred: " + ex.getMessage()));
            }
        }
        return IncidentDetail.of(incident);
    }

    public IncidentDetail reclassify(Long id, Long categoryId, Priority priorityOverride, String reason) {
        Incident i = get(id);
        User me = currentUser.get();
        assertRole(me, Role.ADMIN, Role.TECHNICIAN);
        IncidentCategory cat = categoryId == null ? i.getCategory()
                : categories.findById(categoryId).orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        Priority previous = i.getPriority();
        if (priorityOverride != null) {
            i.overridePriority(priorityOverride, "Manual override by " + me.getUsername() + ": " + reason);
        } else {
            PriorityResult pr = strategies.priorityStrategy().calculate(new PriorityContext(
                    i.getImpact(), i.getUrgency(), i.getService().getCriticality(), i.getAffectedUsers(), i.getService().getUserBase()));
            i.overridePriority(pr.priority(), pr.rationale());
        }
        i.classify(cat, i.getPriority(), i.getPriorityScore() == null ? 0 : i.getPriorityScore(), i.getPriorityRationale());
        if (previous != i.getPriority()) {
            sla.apply(i, LocalDateTime.now());
            bus.publish(IncidentEvent.of(IncidentEventType.PRIORITY_CHANGED, i, me.getUsername(),
                    "Priority changed " + previous + " → " + i.getPriority() + ". " + (reason == null ? "" : reason)));
        }
        bus.publish(IncidentEvent.of(IncidentEventType.CLASSIFIED, i, me.getUsername(), "Re-classified as " + cat.getName()));
        return IncidentDetail.of(i);
    }

    // ------------------------------------------------------------------ 7-8: assignment

    public IncidentDetail autoAssign(Long id, String strategyName) {
        Incident i = get(id);
        assertRole(currentUser.get(), Role.ADMIN);
        return IncidentDetail.of(autoAssignInternal(i, strategyName, currentUser.get().getUsername()));
    }

    private Incident autoAssignInternal(Incident i, String strategyName, String actor) {
        Technician t = assignment.choose(i, strategyName);
        IncidentStatus prev = i.getStatus();
        i.assign(t);
        bus.publish(IncidentEvent.of(IncidentEventType.ASSIGNED, i, actor, prev,
                "Assigned to " + t.getFullName() + " by strategy " + (strategyName == null ? strategies.activeAssignmentName() : strategyName)));
        return i;
    }

    public IncidentDetail assignManually(Long id, Long technicianId, String reason) {
        Incident i = get(id);
        User me = currentUser.get();
        assertRole(me, Role.ADMIN);
        Technician t = technicians.findById(technicianId).orElseThrow(() -> new ResourceNotFoundException("Technician", technicianId));
        assignment.validateManual(t);
        IncidentStatus prev = i.getStatus();
        i.assign(t);
        bus.publish(IncidentEvent.of(IncidentEventType.ASSIGNED, i, me.getUsername(), prev,
                "Manually assigned to " + t.getFullName() + (reason == null ? "" : ": " + reason)));
        return IncidentDetail.of(i);
    }

    // ------------------------------------------------------------------ 10: investigation

    public IncidentDetail acknowledge(Long id) {
        Incident i = get(id); User me = currentUser.get();
        assertAssignedOrAdmin(i, me);
        IncidentStatus prev = i.getStatus();
        i.acknowledge();
        bus.publish(IncidentEvent.of(IncidentEventType.ACKNOWLEDGED, i, me.getUsername(), prev,
                "Acknowledged" + (i.getSla().isResponseViolated() ? " (response SLA missed)" : " within response SLA")));
        return IncidentDetail.of(i);
    }

    public IncidentDetail startInvestigation(Long id) {
        Incident i = get(id); User me = currentUser.get();
        assertAssignedOrAdmin(i, me);
        IncidentStatus prev = i.getStatus();
        i.startInvestigation();
        bus.publish(IncidentEvent.of(IncidentEventType.INVESTIGATION_STARTED, i, me.getUsername(), prev, "Investigation started"));
        return IncidentDetail.of(i);
    }

    public IncidentDetail addNote(Long id, String content) {
        Incident i = get(id); User me = currentUser.get();
        if (me.getRole() == Role.EMPLOYEE && !i.isReportedBy(me)) throw new UnauthorizedOperationException("Not your incident");
        if (me.getRole() == Role.TECHNICIAN && !i.isAssignedTo(me)) throw new UnauthorizedOperationException("Incident not assigned to you");
        if (i.getStatus().isTerminal()) throw new InvalidStateTransitionException(i.getStatus(), "addNote");
        NoteType type = me.getRole() == Role.EMPLOYEE ? NoteType.COMMENT : NoteType.INVESTIGATION;
        i.addNote(me, type, content);
        bus.publish(IncidentEvent.of(IncidentEventType.NOTE_ADDED, i, me.getUsername(), truncate(content)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail requestInfo(Long id, String question) {
        Incident i = get(id); User me = currentUser.get();
        assertAssignedOrAdmin(i, me);
        IncidentStatus prev = i.getStatus();
        i.requestInfo();
        i.addNote(me, NoteType.INFO_REQUEST, question);
        bus.publish(IncidentEvent.of(IncidentEventType.INFO_REQUESTED, i, me.getUsername(), prev, truncate(question)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail provideInfo(Long id, String answer) {
        Incident i = get(id); User me = currentUser.get();
        if (!i.isReportedBy(me) && me.getRole() != Role.ADMIN) throw new UnauthorizedOperationException("Only the reporter can respond");
        IncidentStatus prev = i.getStatus();
        i.provideInfo();
        i.addNote(me, NoteType.INFO_RESPONSE, answer);
        bus.publish(IncidentEvent.of(IncidentEventType.INFO_PROVIDED, i, me.getUsername(), prev, truncate(answer)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail updateDetails(Long id, UpdateIncident req) {
        Incident i = get(id); User me = currentUser.get();
        if (me.getRole() == Role.EMPLOYEE && !i.isReportedBy(me)) throw new UnauthorizedOperationException("Not your incident");
        Impact oldImpact = i.getImpact(); Urgency oldUrg = i.getUrgency();
        i.updateDetails(req.title(), req.description(), req.impact(), req.urgency(), req.affectedUsers() == null ? -1 : req.affectedUsers());
        if (oldImpact != i.getImpact() || oldUrg != i.getUrgency()) {
            Priority previous = i.getPriority();
            PriorityResult pr = strategies.priorityStrategy().calculate(new PriorityContext(
                    i.getImpact(), i.getUrgency(), i.getService().getCriticality(), i.getAffectedUsers(), i.getService().getUserBase()));
            if (pr.priority() != previous) {
                i.overridePriority(pr.priority(), pr.rationale());
                sla.apply(i, LocalDateTime.now());
                bus.publish(IncidentEvent.of(IncidentEventType.PRIORITY_CHANGED, i, "system",
                        "Priority re-evaluated " + previous + " → " + pr.priority() + " after details changed"));
            }
        }
        bus.publish(IncidentEvent.of(IncidentEventType.NOTE_ADDED, i, me.getUsername(), "Details updated"));
        return IncidentDetail.of(i);
    }

    // ------------------------------------------------------------------ 13: resolve / verify / close

    public IncidentDetail resolve(Long id, String summary, String rootCause) {
        Incident i = get(id); User me = currentUser.get();
        assertAssignedOrAdmin(i, me);
        IncidentStatus prev = i.getStatus();
        i.resolve(new Resolution(summary, rootCause, me.getUsername(), LocalDateTime.now()));
        bus.publish(IncidentEvent.of(IncidentEventType.RESOLVED, i, me.getUsername(), prev,
                "Resolved" + (i.getSla().isResolutionViolated() ? " (resolution SLA missed)" : " within SLA") + ": " + truncate(summary)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail verify(Long id) {
        Incident i = get(id); User me = currentUser.get();
        if (!i.isReportedBy(me) && me.getRole() != Role.ADMIN) throw new UnauthorizedOperationException("Only the reporter can verify");
        IncidentStatus prev = i.getStatus();
        i.verify();
        bus.publish(IncidentEvent.of(IncidentEventType.VERIFIED, i, me.getUsername(), prev, "Reporter verified the fix"));
        return IncidentDetail.of(i);
    }

    public IncidentDetail reopen(Long id, String reason) {
        Incident i = get(id); User me = currentUser.get();
        if (!i.isReportedBy(me) && me.getRole() != Role.ADMIN) throw new UnauthorizedOperationException("Only the reporter can reopen");
        IncidentStatus prev = i.getStatus();
        i.reopen();
        i.addNote(me, NoteType.COMMENT, "Reopened: " + reason);
        bus.publish(IncidentEvent.of(IncidentEventType.REOPENED, i, me.getUsername(), prev, truncate(reason)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail close(Long id, String notes) {
        Incident i = get(id); User me = currentUser.get();
        // Reporter may close their own verified incident; admin may close verified or resolved ones.
        if (me.getRole() != Role.ADMIN && !i.isReportedBy(me)) throw new UnauthorizedOperationException("Only the reporter or an admin can close");
        if (me.getRole() != Role.ADMIN && i.getStatus() != IncidentStatus.VERIFIED) throw new InvalidStateTransitionException(i.getStatus(), "close");
        IncidentStatus prev = i.getStatus();
        i.close(me, notes);
        bus.publish(IncidentEvent.of(IncidentEventType.CLOSED, i, me.getUsername(), prev, notes == null ? "Closed" : truncate(notes)));
        return IncidentDetail.of(i);
    }

    public IncidentDetail feedback(Long id, int rating, String comment) {
        Incident i = get(id); User me = currentUser.get();
        if (!i.isReportedBy(me)) throw new UnauthorizedOperationException("Only the reporter can give feedback");
        i.giveFeedback(rating, comment);
        bus.publish(IncidentEvent.of(IncidentEventType.FEEDBACK_GIVEN, i, me.getUsername(), "Rated " + rating + "/5" + (comment == null ? "" : ": " + truncate(comment))));
        return IncidentDetail.of(i);
    }

    // ------------------------------------------------------------------ guards

    private void assertCanView(Incident i, User me) {
        switch (me.getRole()) {
            case ADMIN -> { }
            case TECHNICIAN -> { /* technicians may view any incident (to spot related ones) */ }
            case EMPLOYEE -> { if (!i.isReportedBy(me)) throw new UnauthorizedOperationException("You can only view incidents you reported"); }
        }
    }

    private void assertAssignedOrAdmin(Incident i, User me) {
        if (me.getRole() == Role.ADMIN) return;
        if (me.getRole() == Role.TECHNICIAN && i.isAssignedTo(me)) return;
        throw new UnauthorizedOperationException("Only the assigned technician or an admin may perform this action");
    }

    private void assertRole(User me, Role... allowed) {
        for (Role r : allowed) if (me.getRole() == r) return;
        throw new UnauthorizedOperationException("Operation requires role " + List.of(allowed));
    }

    private static String truncate(String s) { return s == null ? null : s.length() > 200 ? s.substring(0, 197) + "..." : s; }

    /** Used by background services (SLA monitor / escalation) that have no HTTP principal. */
    public Incident loadForSystem(Long id) { return incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id)); }
    public IncidentEventBus bus() { return bus; }
    public boolean sameUser(User a, User b) { return a != null && b != null && Objects.equals(a.getId(), b.getId()); }
}
