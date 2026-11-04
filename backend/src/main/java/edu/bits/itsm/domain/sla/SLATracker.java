package edu.bits.itsm.domain.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Value object embedded in Incident that tracks SLA deadlines and outcomes.
 * <p>
 * <b>Composition</b>: an SLATracker has no identity of its own and lives and
 * dies with its incident. All the SLA arithmetic is encapsulated here so the
 * service layer only ever asks questions ("is it at risk?") instead of doing
 * date maths on raw fields.
 */
@Embeddable
public class SLATracker {

    @Column(name = "sla_started_at")
    private LocalDateTime startedAt;

    @Column(name = "sla_response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "sla_resolution_deadline")
    private LocalDateTime resolutionDeadline;

    @Column(name = "sla_responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "sla_resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "sla_response_violated")
    private boolean responseViolated;

    @Column(name = "sla_resolution_violated")
    private boolean resolutionViolated;

    @Column(name = "sla_warning_sent")
    private boolean warningSent;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", length = 20)
    private SLAStatus status = SLAStatus.NOT_APPLICABLE;

    public SLATracker() { }

    /** (Re)starts the SLA clock from {@code from} using the given policy. */
    public void applyPolicy(SLAPolicy policy, LocalDateTime from) {
        this.startedAt = from;
        this.responseDeadline = from.plusMinutes(policy.getResponseMinutes());
        this.resolutionDeadline = from.plusMinutes(policy.getResolutionMinutes());
        this.status = SLAStatus.ON_TRACK;
        this.warningSent = false;
        this.responseViolated = false;
        this.resolutionViolated = false;
    }

    /** First technician response (acknowledgement). Idempotent. */
    public void markResponded(LocalDateTime at) {
        if (respondedAt == null) {
            respondedAt = at;
            if (responseDeadline != null && at.isAfter(responseDeadline)) responseViolated = true;
        }
    }

    public void markResolved(LocalDateTime at) {
        resolvedAt = at;
        if (resolutionDeadline != null && at.isAfter(resolutionDeadline)) resolutionViolated = true;
        status = isViolated() ? SLAStatus.VIOLATED : SLAStatus.MET;
    }

    /** Called when a resolved incident is reopened by the reporter. */
    public void reopen() {
        resolvedAt = null;
        status = isViolated() ? SLAStatus.VIOLATED : SLAStatus.ON_TRACK;
    }

    /**
     * Re-evaluates live SLA health for an unresolved incident.
     * @return true if the status changed (the monitor uses this to decide whether to raise events)
     */
    public boolean evaluate(LocalDateTime now, int warningThresholdPct) {
        if (resolvedAt != null || resolutionDeadline == null || startedAt == null) return false;
        SLAStatus previous = status;

        if (respondedAt == null && responseDeadline != null && now.isAfter(responseDeadline)) responseViolated = true;
        if (now.isAfter(resolutionDeadline)) resolutionViolated = true;

        if (isViolated()) {
            status = SLAStatus.VIOLATED;
        } else {
            status = percentRemaining(now) <= warningThresholdPct ? SLAStatus.AT_RISK : SLAStatus.ON_TRACK;
        }
        return previous != status;
    }

    /** Percentage of the resolution window still remaining (0..100). */
    public double percentRemaining(LocalDateTime now) {
        if (startedAt == null || resolutionDeadline == null) return 100;
        long total = Duration.between(startedAt, resolutionDeadline).toSeconds();
        long remaining = Duration.between(now, resolutionDeadline).toSeconds();
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100, remaining * 100.0 / total));
    }

    public long minutesRemainingToResolution(LocalDateTime now) {
        return resolutionDeadline == null ? Long.MAX_VALUE : Duration.between(now, resolutionDeadline).toMinutes();
    }

    public boolean isViolated() { return responseViolated || resolutionViolated; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getResponseDeadline() { return responseDeadline; }
    public LocalDateTime getResolutionDeadline() { return resolutionDeadline; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public boolean isResponseViolated() { return responseViolated; }
    public boolean isResolutionViolated() { return resolutionViolated; }
    public boolean isWarningSent() { return warningSent; }
    public void setWarningSent(boolean warningSent) { this.warningSent = warningSent; }
    public SLAStatus getStatus() { return status; }
}
