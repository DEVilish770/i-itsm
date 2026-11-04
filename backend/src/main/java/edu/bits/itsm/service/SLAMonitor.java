package edu.bits.itsm.service;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentEventBus;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.repository.IncidentRepository;
import edu.bits.itsm.repository.TechnicianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Background processing for continuous SLA monitoring (requirement) and
 * intelligent feature C — automatic escalation, and E — SLA risk detection.
 * <p>
 * Runs every {@code itsm.sla.monitor-seconds}. For each open incident:
 * <ul>
 *   <li>re-evaluates SLA health; raises SLA_WARNING once when AT_RISK, SLA_VIOLATED when breached</li>
 *   <li>if a P1/P2 is still unacknowledged after {@code itsm.escalation.minutes}, escalates:
 *       level 1 → reassign to a team lead, level 2 → notify administrators</li>
 * </ul>
 */
@Service
public class SLAMonitor {

    private static final Logger log = LoggerFactory.getLogger(SLAMonitor.class);
    private static final List<IncidentStatus> OPEN = Arrays.stream(IncidentStatus.values()).filter(IncidentStatus::isOpen).toList();
    private static final List<IncidentStatus> UNACKED = List.of(IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED);

    private final IncidentRepository incidents;
    private final TechnicianRepository technicians;
    private final SLAService sla;
    private final IncidentEventBus bus;
    private final int escalationMinutes;

    public SLAMonitor(IncidentRepository incidents, TechnicianRepository technicians, SLAService sla, IncidentEventBus bus,
                      @Value("${itsm.escalation.minutes:10}") int escalationMinutes) {
        this.incidents = incidents; this.technicians = technicians; this.sla = sla; this.bus = bus;
        this.escalationMinutes = escalationMinutes;
    }

    @Scheduled(fixedDelayString = "${itsm.sla.monitor-seconds:30}000", initialDelay = 15000)
    @Transactional
    public void tick() {
        int warnings = 0, violations = 0, escalations = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Incident i : incidents.findByStatusIn(OPEN)) {
            if (i.getPriority() == null) continue;
            int warnPct = sla.policyFor(i.getPriority()).getWarningThresholdPct();
            boolean changed = i.getSla().evaluate(now, warnPct);
            if (changed && i.getSla().getStatus() == SLAStatus.AT_RISK && !i.getSla().isWarningSent()) {
                i.getSla().setWarningSent(true);
                warnings++;
                bus.publish(IncidentEvent.of(IncidentEventType.SLA_WARNING, i, "sla-monitor",
                        String.format("Only %d min left to resolution deadline (%.0f%% of window)", i.getSla().minutesRemainingToResolution(now), i.getSla().percentRemaining(now))));
            } else if (changed && i.getSla().getStatus() == SLAStatus.VIOLATED) {
                violations++;
                bus.publish(IncidentEvent.of(IncidentEventType.SLA_VIOLATED, i, "sla-monitor",
                        (i.getSla().isResponseViolated() ? "Response" : "Resolution") + " SLA breached for " + i.getPriority()));
            }
            if (escalateIfNeeded(i, now)) escalations++;
        }
        if (warnings + violations + escalations > 0)
            log.info("SLA monitor: {} warnings, {} violations, {} escalations", warnings, violations, escalations);
    }

    private boolean escalateIfNeeded(Incident i, LocalDateTime now) {
        boolean critical = i.getPriority() == Priority.P1 || i.getPriority() == Priority.P2;
        if (!critical || !UNACKED.contains(i.getStatus())) return false;
        LocalDateTime since = i.getUpdatedAt();
        long waited = java.time.Duration.between(since, now).toMinutes();
        long needed = (long) escalationMinutes * (i.getEscalationLevel() + 1);
        if (waited < needed) return false;

        i.escalate();
        String desc;
        if (i.getEscalationLevel() == 1) {
            Technician lead = technicians.findByActiveTrueAndTeamLeadTrue().stream()
                    .filter(Technician::isAvailable)
                    .filter(t -> i.getAssignedTechnician() == null || !t.getId().equals(i.getAssignedTechnician().getId()))
                    .findFirst().orElse(null);
            if (lead != null && i.getStatus() == IncidentStatus.ASSIGNED) {
                i.assign(lead);
                desc = "Level 1: unacknowledged for " + waited + " min → reassigned to team lead " + lead.getFullName();
            } else {
                desc = "Level 1: unacknowledged for " + waited + " min → team lead notified";
            }
        } else {
            desc = "Level " + i.getEscalationLevel() + ": still unacknowledged after " + waited + " min → administrators alerted";
        }
        bus.publish(IncidentEvent.of(IncidentEventType.ESCALATED, i, "escalation-engine", desc));
        return true;
    }
}
