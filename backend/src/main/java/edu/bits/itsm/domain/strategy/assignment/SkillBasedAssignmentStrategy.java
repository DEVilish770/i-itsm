package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.incident.Incident;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks an available technician whose skills cover the incident category; ties broken by lower workload, then experience. */
public class SkillBasedAssignmentStrategy implements AssignmentStrategy {

    @Override public String name() { return "SKILL_BASED"; }
    @Override public String description() { return "Must have the matching skill; least loaded, then most experienced"; }

    @Override
    public Optional<TechnicianCandidate> select(Incident incident, List<TechnicianCandidate> candidates) {
        return candidates.stream()
                .filter(c -> c.technician().isAvailable() && c.hasCapacity())
                .filter(c -> c.technician().hasSkill(incident.getCategory()))
                .min(Comparator.comparingInt(TechnicianCandidate::activeWorkload)
                        .thenComparing(c -> -c.technician().getExperienceYears()));
    }
}
