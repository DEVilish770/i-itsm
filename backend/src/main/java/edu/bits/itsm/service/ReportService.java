package edu.bits.itsm.service;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAStatus;
import edu.bits.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/** Aggregations for dashboards and reports. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final List<IncidentStatus> OPEN = Arrays.stream(IncidentStatus.values()).filter(IncidentStatus::isOpen).toList();

    private final IncidentRepository incidents;

    public ReportService(IncidentRepository incidents) { this.incidents = incidents; }

    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalIncidents", incidents.count());
        m.put("openIncidents", incidents.countByStatusIn(OPEN));
        m.put("criticalIncidents", incidents.countByPriority(Priority.P1));
        m.put("resolvedIncidents", incidents.countByStatusIn(List.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED)));
        m.put("slaViolations", incidents.countBySlaStatus(SLAStatus.VIOLATED));
        m.put("slaAtRisk", incidents.countBySlaStatus(SLAStatus.AT_RISK));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) byStatus.put(s.name(), incidents.countByStatus(s));
        m.put("byStatus", byStatus);
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) byPriority.put(p.name(), incidents.countByPriority(p));
        m.put("byPriority", byPriority);
        return m;
    }

    public List<Map<String, Object>> technicianWorkload() {
        return incidents.workloadByTechnician(OPEN).stream()
                .map(r -> Map.<String, Object>of("technician", r[0], "activeIncidents", r[1])).toList();
    }

    public List<Map<String, Object>> serviceAnalysis() {
        return incidents.countByService().stream().map(r -> Map.<String, Object>of("service", r[0], "incidents", r[1])).toList();
    }

    public List<Map<String, Object>> categoryAnalysis() {
        return incidents.countByCategory().stream().map(r -> Map.<String, Object>of("category", r[0], "incidents", r[1])).toList();
    }

    /** Mean time to acknowledge / resolve in minutes, plus SLA compliance % over resolved incidents. */
    public Map<String, Object> performance() {
        List<Incident> done = incidents.findByStatusIn(List.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED));
        double mtta = done.stream().filter(i -> i.getAcknowledgedAt() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getAcknowledgedAt()).toMinutes()).average().orElse(0);
        double mttr = done.stream().filter(i -> i.getResolution() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getResolution().getResolvedAt()).toMinutes()).average().orElse(0);
        long met = done.stream().filter(i -> !i.getSla().isViolated()).count();
        double avgRating = done.stream().filter(i -> i.getFeedback() != null).mapToInt(i -> i.getFeedback().getRating()).average().orElse(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resolvedCount", done.size());
        m.put("meanTimeToAcknowledgeMinutes", Math.round(mtta));
        m.put("meanTimeToResolveMinutes", Math.round(mttr));
        m.put("slaCompliancePct", done.isEmpty() ? 100 : Math.round(met * 1000.0 / done.size()) / 10.0);
        m.put("averageFeedbackRating", Math.round(avgRating * 10) / 10.0);
        return m;
    }
}
