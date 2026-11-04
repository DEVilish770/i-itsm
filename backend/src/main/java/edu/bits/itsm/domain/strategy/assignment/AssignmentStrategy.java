package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.incident.Incident;

import java.util.List;
import java.util.Optional;

/**
 * <b>Strategy pattern</b>: interchangeable technician-selection algorithms.
 * Implementations are pure functions over the candidate list, which makes them trivially unit-testable.
 */
public interface AssignmentStrategy {
    String name();
    String description();
    /** @return the chosen candidate, or empty if nobody suitable is available. */
    Optional<TechnicianCandidate> select(Incident incident, List<TechnicianCandidate> candidates);
}
