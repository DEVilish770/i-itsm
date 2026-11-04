package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.incident.Incident;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks the available technician with the lowest active workload regardless of skill (prefers skilled ones on ties). */
public class LoadBalancedAssignmentStrategy implements AssignmentStrategy {

    @Override public String name() { return "LOAD_BALANCED"; }
    @Override public String description() { return "Least loaded available technician; skill match only breaks ties"; }

    @Override
    public Optional<TechnicianCandidate> select(Incident incident, List<TechnicianCandidate> candidates) {
        return candidates.stream()
                .filter(c -> c.technician().isAvailable() && c.hasCapacity())
                .min(Comparator.comparingInt(TechnicianCandidate::activeWorkload)
                        .thenComparing(c -> c.technician().hasSkill(incident.getCategory()) ? 0 : 1));
    }
}
