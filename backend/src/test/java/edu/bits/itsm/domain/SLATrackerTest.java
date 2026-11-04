package edu.bits.itsm.domain;

import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.sla.SLAPolicy;
import edu.bits.itsm.domain.sla.SLAStatus;
import edu.bits.itsm.domain.sla.SLATracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** TC-09 .. TC-12: SLA calculation, deadline tracking, warning threshold and violation detection. */
class SLATrackerTest {

    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 4, 10, 0);
    private final SLAPolicy p1 = new SLAPolicy(Priority.P1, 5, 30);

    @Test @DisplayName("TC-09 Normal: deadlines are computed from the policy")
    void deadlines() {
        SLATracker s = new SLATracker();
        s.applyPolicy(p1, t0);
        assertEquals(t0.plusMinutes(5), s.getResponseDeadline());
        assertEquals(t0.plusMinutes(30), s.getResolutionDeadline());
        assertEquals(SLAStatus.ON_TRACK, s.getStatus());
    }

    @Test @DisplayName("TC-10 Boundary: warning raised exactly when remaining window ≤ threshold")
    void warningThreshold() {
        SLATracker s = new SLATracker();
        s.applyPolicy(p1, t0);
        s.markResponded(t0.plusMinutes(1));
        assertFalse(s.evaluate(t0.plusMinutes(20), 25), "66% left: no change");
        assertEquals(SLAStatus.ON_TRACK, s.getStatus());
        assertTrue(s.evaluate(t0.plusMinutes(23), 25), "23.3% left → AT_RISK");
        assertEquals(SLAStatus.AT_RISK, s.getStatus());
    }

    @Test @DisplayName("TC-11 Exception path: response deadline missed → VIOLATED even before resolution deadline")
    void responseViolation() {
        SLATracker s = new SLATracker();
        s.applyPolicy(p1, t0);
        assertTrue(s.evaluate(t0.plusMinutes(6), 25));
        assertEquals(SLAStatus.VIOLATED, s.getStatus());
        assertTrue(s.isResponseViolated());
        assertFalse(s.isResolutionViolated());
    }

    @Test @DisplayName("TC-12 Normal/boundary: resolution inside the window → MET; one second late → VIOLATED")
    void resolutionOutcome() {
        SLATracker met = new SLATracker();
        met.applyPolicy(p1, t0); met.markResponded(t0.plusMinutes(2)); met.markResolved(t0.plusMinutes(30));
        assertEquals(SLAStatus.MET, met.getStatus(), "exactly at the deadline still counts as met");

        SLATracker late = new SLATracker();
        late.applyPolicy(p1, t0); late.markResponded(t0.plusMinutes(2)); late.markResolved(t0.plusMinutes(30).plusSeconds(1));
        assertEquals(SLAStatus.VIOLATED, late.getStatus());
        assertTrue(late.isResolutionViolated());
    }

    @Test @DisplayName("TC-13 Invalid input: SLA policy rejects non-positive targets and bad thresholds")
    void policyValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SLAPolicy(Priority.P3, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new SLAPolicy(Priority.P3, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> p1.setWarningThresholdPct(100));
    }
}
