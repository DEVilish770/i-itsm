package edu.bits.itsm.domain.incident;

import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Concrete states of the State pattern plus a lookup from the persisted enum.
 * States are stateless singletons; all mutable data lives in the Incident.
 *
 * Lifecycle:
 *   NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS ⇄ PENDING_INFO
 *   IN_PROGRESS → RESOLVED → VERIFIED → CLOSED,   RESOLVED → (reopen) IN_PROGRESS
 *   Re-assignment is allowed from ASSIGNED / ACKNOWLEDGED / IN_PROGRESS (escalation).
 *   Re-classification / priority override after CLASSIFIED is not a transition: see Incident.reclassify.
 */
public final class IncidentStates {

    private IncidentStates() { }

    private static final Map<IncidentStatus, IncidentState> REGISTRY = new EnumMap<>(IncidentStatus.class);

    static {
        register(new NewState());
        register(new ClassifiedState());
        register(new AssignedState());
        register(new AcknowledgedState());
        register(new InProgressState());
        register(new PendingInfoState());
        register(new ResolvedState());
        register(new VerifiedState());
        register(new ClosedState());
    }

    private static void register(IncidentState s) { REGISTRY.put(s.status(), s); }

    public static IncidentState of(IncidentStatus status) {
        IncidentState s = REGISTRY.get(status);
        if (s == null) throw new IllegalStateException("No state registered for " + status);
        return s;
    }

    // ---------------------------------------------------------------- states

    static final class NewState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.NEW; }
        public Set<String> allowedActions() { return Set.of("classify"); }
        @Override public void classify(Incident i) { i.moveTo(IncidentStatus.CLASSIFIED); }
    }

    static final class ClassifiedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.CLASSIFIED; }
        public Set<String> allowedActions() { return Set.of("assign", "classify"); }
        @Override public void classify(Incident i) { /* re-classification keeps the state */ }
        @Override public void assign(Incident i, Technician t) { i.attachTechnician(t); i.moveTo(IncidentStatus.ASSIGNED); }
    }

    static final class AssignedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.ASSIGNED; }
        public Set<String> allowedActions() { return Set.of("acknowledge", "assign"); }
        @Override public void assign(Incident i, Technician t) { i.attachTechnician(t); }
        @Override public void acknowledge(Incident i) {
            i.getSla().markResponded(LocalDateTime.now());
            i.setAcknowledgedAt(LocalDateTime.now());
            i.moveTo(IncidentStatus.ACKNOWLEDGED);
        }
    }

    static final class AcknowledgedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.ACKNOWLEDGED; }
        public Set<String> allowedActions() { return Set.of("startInvestigation", "assign"); }
        @Override public void assign(Incident i, Technician t) { i.attachTechnician(t); i.moveTo(IncidentStatus.ASSIGNED); }
        @Override public void startInvestigation(Incident i) { i.moveTo(IncidentStatus.IN_PROGRESS); }
    }

    static final class InProgressState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.IN_PROGRESS; }
        public Set<String> allowedActions() { return Set.of("requestInfo", "resolve", "assign"); }
        @Override public void assign(Incident i, Technician t) { i.attachTechnician(t); i.moveTo(IncidentStatus.ASSIGNED); }
        @Override public void requestInfo(Incident i) { i.moveTo(IncidentStatus.PENDING_INFO); }
        @Override public void resolve(Incident i, Resolution r) {
            i.attachResolution(r);
            i.getSla().markResolved(r.getResolvedAt());
            i.moveTo(IncidentStatus.RESOLVED);
        }
    }

    static final class PendingInfoState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.PENDING_INFO; }
        public Set<String> allowedActions() { return Set.of("provideInfo"); }
        @Override public void provideInfo(Incident i) { i.moveTo(IncidentStatus.IN_PROGRESS); }
    }

    static final class ResolvedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.RESOLVED; }
        public Set<String> allowedActions() { return Set.of("verify", "reopen", "close"); }
        @Override public void verify(Incident i) { i.moveTo(IncidentStatus.VERIFIED); }
        @Override public void reopen(Incident i) {
            i.getSla().reopen();
            i.moveTo(IncidentStatus.IN_PROGRESS);
        }
        /** Admin may close directly from RESOLVED if the reporter never verifies (auto-close policy). */
        @Override public void close(Incident i, User by, String notes) { i.recordClosure(by, notes); i.moveTo(IncidentStatus.CLOSED); }
    }

    static final class VerifiedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.VERIFIED; }
        public Set<String> allowedActions() { return Set.of("close"); }
        @Override public void close(Incident i, User by, String notes) { i.recordClosure(by, notes); i.moveTo(IncidentStatus.CLOSED); }
    }

    static final class ClosedState implements IncidentState {
        public IncidentStatus status() { return IncidentStatus.CLOSED; }
        public Set<String> allowedActions() { return Set.of(); }
    }
}
