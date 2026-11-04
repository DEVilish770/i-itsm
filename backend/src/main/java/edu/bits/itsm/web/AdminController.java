package edu.bits.itsm.web;

import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.notification.SimulatedEmailGateway;
import edu.bits.itsm.repository.AuditRecordRepository;
import edu.bits.itsm.repository.NotificationRepository;
import edu.bits.itsm.service.AdminService;
import edu.bits.itsm.service.AssignmentService;
import edu.bits.itsm.service.ReportService;
import edu.bits.itsm.service.SLAMonitor;
import edu.bits.itsm.service.SLAService;
import edu.bits.itsm.web.dto.AdminRequests.*;
import edu.bits.itsm.web.dto.Views.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Everything under /api/admin requires ROLE_ADMIN (enforced in SecurityConfig and again via @PreAuthorize on AdminService). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final ReportService reports;
    private final SLAService sla;
    private final SLAMonitor monitor;
    private final AssignmentService assignment;
    private final AuditRecordRepository audit;
    private final NotificationRepository notifications;
    private final SimulatedEmailGateway emailOutbox;

    public AdminController(AdminService admin, ReportService reports, SLAService sla, SLAMonitor monitor, AssignmentService assignment,
                           AuditRecordRepository audit, NotificationRepository notifications, SimulatedEmailGateway emailOutbox) {
        this.admin = admin; this.reports = reports; this.sla = sla; this.monitor = monitor; this.assignment = assignment;
        this.audit = audit; this.notifications = notifications; this.emailOutbox = emailOutbox;
    }

    // users & technicians
    @GetMapping("/users") public List<UserView> users() {
        return admin.allUsers().stream().map(u -> u instanceof Technician t ? UserView.of(t, assignment.workloadOf(t)) : UserView.of(u)).toList();
    }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUser r) { return UserView.of(admin.createUser(r)); }
    @PatchMapping("/users/{id}/active")
    public UserView setActive(@PathVariable Long id, @RequestParam boolean active) { return UserView.of(admin.setActive(id, active)); }
    @PutMapping("/technicians/{id}")
    public UserView updateTechnician(@PathVariable Long id, @Valid @RequestBody UpdateTechnician r) {
        Technician t = admin.updateTechnician(id, r); return UserView.of(t, assignment.workloadOf(t));
    }

    // catalogue
    @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED)
    public ServiceView createService(@Valid @RequestBody ServiceReq r) { return ServiceView.of(admin.createService(r)); }
    @PutMapping("/services/{id}")
    public ServiceView updateService(@PathVariable Long id, @Valid @RequestBody ServiceReq r) { return ServiceView.of(admin.updateService(id, r)); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryReq r) { return CategoryView.of(admin.createCategory(r)); }
    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryReq r) { return CategoryView.of(admin.updateCategory(id, r)); }

    // SLA & strategies
    @PutMapping("/sla-policies")
    public Map<String, Object> updateSla(@Valid @RequestBody SlaReq r) {
        var p = sla.update(r.priority(), r.responseMinutes(), r.resolutionMinutes(), r.warningThresholdPct() == null ? 25 : r.warningThresholdPct());
        return Map.of("priority", p.getPriority().name(), "responseMinutes", p.getResponseMinutes(), "resolutionMinutes", p.getResolutionMinutes(), "warningThresholdPct", p.getWarningThresholdPct());
    }
    @PutMapping("/strategies")
    public void configureStrategies(@RequestBody StrategyReq r) { admin.configureStrategies(r); }

    /** Lets the demo trigger an SLA sweep on demand instead of waiting for the scheduler. */
    @PostMapping("/sla/run-monitor")
    public Map<String, String> runMonitor() { monitor.tick(); return Map.of("status", "ok"); }

    // reports
    @GetMapping("/reports/overview")    public Map<String, Object> overview() { return reports.overview(); }
    @GetMapping("/reports/workload")    public List<Map<String, Object>> workload() { return reports.technicianWorkload(); }
    @GetMapping("/reports/services")    public List<Map<String, Object>> services() { return reports.serviceAnalysis(); }
    @GetMapping("/reports/categories")  public List<Map<String, Object>> categories() { return reports.categoryAnalysis(); }
    @GetMapping("/reports/performance") public Map<String, Object> performance() { return reports.performance(); }

    // audit & notifications
    @GetMapping("/audit")
    public Page<AuditView> auditLog(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return audit.findAllByOrderByTimestampDesc(PageRequest.of(page, Math.min(size, 200))).map(AuditView::of);
    }
    @GetMapping("/notifications")
    public List<NotificationView> allNotifications() { return notifications.findTop100ByOrderBySentAtDesc().stream().map(NotificationView::of).toList(); }
    @GetMapping("/email-outbox")
    public List<String> emailOutbox() { return emailOutbox.recent(); }
}
