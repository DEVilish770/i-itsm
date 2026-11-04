package edu.bits.itsm.domain.sla;

import edu.bits.itsm.domain.incident.Priority;
import jakarta.persistence.*;

/**
 * Response/resolution targets for a given priority. One row per priority.
 * Admin-configurable; the SLA engine reads these instead of hard-coding targets.
 */
@Entity
@Table(name = "sla_policies")
public class SLAPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 5)
    private Priority priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    /** Percentage of the window remaining at which a warning is raised (e.g. 25 = warn when 25% of time is left). */
    @Column(name = "warning_threshold_pct", nullable = false)
    private int warningThresholdPct = 25;

    protected SLAPolicy() { }

    public SLAPolicy(Priority priority, int responseMinutes, int resolutionMinutes) {
        this.priority = priority;
        setResponseMinutes(responseMinutes);
        setResolutionMinutes(resolutionMinutes);
    }

    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public int getResponseMinutes() { return responseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public int getWarningThresholdPct() { return warningThresholdPct; }

    public void setResponseMinutes(int m) {
        if (m <= 0) throw new IllegalArgumentException("responseMinutes must be positive");
        this.responseMinutes = m;
    }
    public void setResolutionMinutes(int m) {
        if (m <= 0) throw new IllegalArgumentException("resolutionMinutes must be positive");
        this.resolutionMinutes = m;
    }
    public void setWarningThresholdPct(int pct) {
        if (pct < 1 || pct > 99) throw new IllegalArgumentException("warningThresholdPct must be 1..99");
        this.warningThresholdPct = pct;
    }
}
