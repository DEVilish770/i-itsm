package edu.bits.itsm.domain.incident;

/**
 * Persisted lifecycle status. The behaviour attached to each status lives in the
 * State pattern classes under {@code domain.state}; this enum is only the
 * storable "key" of the current state.
 */
public enum IncidentStatus {
    NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO, RESOLVED, VERIFIED, CLOSED;

    public boolean isTerminal() { return this == CLOSED; }

    /** "Open" for reporting purposes = not resolved/verified/closed. */
    public boolean isOpen() { return this != RESOLVED && this != VERIFIED && this != CLOSED; }

    /** Counts toward a technician's active workload. */
    public boolean isActiveForTechnician() { return isOpen(); }
}
