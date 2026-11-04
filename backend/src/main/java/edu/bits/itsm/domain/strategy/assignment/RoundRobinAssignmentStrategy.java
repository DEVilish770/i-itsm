package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.incident.Incident;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Cycles through available technicians in id order, giving everyone a turn. Stateful (a counter), thread-safe. */
public class RoundRobinAssignmentStrategy implements AssignmentStrategy {

    private final AtomicInteger cursor = new AtomicInteger();

    @Override public String name() { return "ROUND_ROBIN"; }
    @Override public String description() { return "Rotates through available technicians in turn"; }

    @Override
    public Optional<TechnicianCandidate> select(Incident incident, List<TechnicianCandidate> candidates) {
        List<TechnicianCandidate> eligible = candidates.stream()
                .filter(c -> c.technician().isAvailable() && c.hasCapacity())
                .sorted(Comparator.comparing(c -> c.technician().getId()))
                .toList();
        if (eligible.isEmpty()) return Optional.empty();
        int idx = Math.floorMod(cursor.getAndIncrement(), eligible.size());
        return Optional.of(eligible.get(idx));
    }
}
