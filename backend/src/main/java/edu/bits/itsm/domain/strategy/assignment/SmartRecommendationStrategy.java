package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.Priority;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Intelligent feature "Smart Technician Recommendation": multi-factor weighted score.
 * <pre>
 *   skillMatch        : 40 if the technician has the category skill, else 0
 *   capacityScore     : 25 × (1 − workload / maxWorkload)
 *   experienceScore   : min(experienceYears, 10) × 2        (max 20)
 *   serviceFamiliarity: min(priorIncidentsOnService, 5) × 3 (max 15)
 *   priorityBoost     : for P1/P2, +10 if teamLead (escalation-friendly)
 * </pre>
 * Highest score wins; technicians without a skill match are still eligible (score penalised) so that
 * a P1 is never left unassigned when the only skilled engineer is overloaded.
 */
public class SmartRecommendationStrategy implements AssignmentStrategy {

    @Override public String name() { return "SMART_RECOMMENDATION"; }
    @Override public String description() { return "Weighted score of skill, capacity, experience and service familiarity"; }

    public double score(Incident incident, TechnicianCandidate c) {
        double skill = c.technician().hasSkill(incident.getCategory()) ? 40 : 0;
        double capacity = 25.0 * (1.0 - (double) c.activeWorkload() / c.technician().getMaxWorkload());
        double exp = Math.min(c.technician().getExperienceYears(), 10) * 2.0;
        double fam = Math.min(c.priorIncidentsOnService(), 5) * 3.0;
        double boost = (incident.getPriority() == Priority.P1 || incident.getPriority() == Priority.P2)
                && c.technician().isTeamLead() ? 10 : 0;
        return skill + capacity + exp + fam + boost;
    }

    @Override
    public Optional<TechnicianCandidate> select(Incident incident, List<TechnicianCandidate> candidates) {
        return candidates.stream()
                .filter(c -> c.technician().isAvailable() && c.hasCapacity())
                .max(Comparator.comparingDouble(c -> score(incident, c)));
    }
}
