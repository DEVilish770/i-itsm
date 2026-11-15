package edu.bits.itsm.web;

import edu.bits.itsm.config.StrategyRegistry;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.repository.ITServiceRepository;
import edu.bits.itsm.repository.IncidentCategoryRepository;
import edu.bits.itsm.repository.NotificationRepository;
import edu.bits.itsm.repository.TechnicianRepository;
import edu.bits.itsm.security.CurrentUser;
import edu.bits.itsm.service.AssignmentService;
import edu.bits.itsm.service.SLAService;
import edu.bits.itsm.web.dto.Views.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Read-only reference data every authenticated user needs, plus the user's own notifications. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final ITServiceRepository services;
    private final IncidentCategoryRepository categories;
    private final TechnicianRepository technicians;
    private final AssignmentService assignment;
    private final SLAService sla;
    private final StrategyRegistry strategies;
    private final NotificationRepository notifications;
    private final CurrentUser currentUser;

    public CatalogController(ITServiceRepository services, IncidentCategoryRepository categories, TechnicianRepository technicians,
                             AssignmentService assignment, SLAService sla, StrategyRegistry strategies,
                             NotificationRepository notifications, CurrentUser currentUser) {
        this.services = services; this.categories = categories; this.technicians = technicians; this.assignment = assignment;
        this.sla = sla; this.strategies = strategies; this.notifications = notifications; this.currentUser = currentUser;
    }

    @GetMapping("/services")   public List<ServiceView> services() { return services.findAll().stream().map(ServiceView::of).toList(); }
    @GetMapping("/categories") public List<CategoryView> categories() { return categories.findAll().stream().map(CategoryView::of).toList(); }

    @GetMapping("/technicians")
    public List<UserView> technicians() { return technicians.findByActiveTrue().stream().map(t -> UserView.of(t, assignment.workloadOf(t))).toList(); }

    @GetMapping("/sla-policies")
    public List<Map<String, Object>> slaPolicies() {
        return sla.all().stream().map(p -> Map.<String, Object>of("id", p.getId(), "priority", p.getPriority().name(),
                "responseMinutes", p.getResponseMinutes(), "resolutionMinutes", p.getResolutionMinutes(), "warningThresholdPct", p.getWarningThresholdPct())).toList();
    }

    @GetMapping("/strategies")
    public Map<String, Object> strategies() {
        return Map.of(
                "activeAssignment", strategies.activeAssignmentName(),
                "activePriority", strategies.activePriorityName(),
                "assignment", strategies.assignmentStrategies().stream().map(s -> Map.of("name", s.name(), "description", s.description())).toList(),
                "priority", strategies.priorityStrategies().stream().map(s -> Map.of("name", s.name(), "description", s.description())).toList());
    }

    @GetMapping("/notifications")
    public List<NotificationView> myNotifications() {
        return notifications.findTop50ByRecipientIdOrderBySentAtDesc(currentUser.get().getId()).stream().map(NotificationView::of).toList();
    }

    /** The bell's count: in-app rows only (the EMAIL / SMS copies of the same event are not "unread" items). */
    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notifications.countByRecipientIdAndChannelAndReadFalse(currentUser.get().getId(), NotificationChannelType.IN_APP));
    }

    /** Only the read flag is written (and only on the caller's own row), so Claude's rewrite landing meanwhile is kept. */
    @PostMapping("/notifications/{id}/read")
    public void markRead(@PathVariable Long id) {
        notifications.markRead(currentUser.get().getId(), List.of(id));
    }

    @PostMapping("/notifications/read-all")
    public void markAllRead() {
        Long me = currentUser.get().getId();
        List<Long> latest = notifications.findTop50ByRecipientIdOrderBySentAtDesc(me).stream().map(Notification::getId).toList();
        if (!latest.isEmpty()) notifications.markRead(me, latest);
    }
}
