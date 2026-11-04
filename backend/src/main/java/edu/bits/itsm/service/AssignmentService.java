package edu.bits.itsm.service;

import edu.bits.itsm.config.StrategyRegistry;
import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.domain.strategy.assignment.AssignmentStrategy;
import edu.bits.itsm.domain.strategy.assignment.SmartRecommendationStrategy;
import edu.bits.itsm.domain.strategy.assignment.TechnicianCandidate;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.exception.TechnicianUnavailableException;
import edu.bits.itsm.exception.WorkloadExceededException;
import edu.bits.itsm.repository.IncidentRepository;
import edu.bits.itsm.repository.TechnicianRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds the candidate list with live workload data and runs the active {@link AssignmentStrategy}. */
@Service
public class AssignmentService {

    private static final List<IncidentStatus> ACTIVE = Arrays.stream(IncidentStatus.values()).filter(IncidentStatus::isActiveForTechnician).toList();

    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;
    private final StrategyRegistry strategies;

    public AssignmentService(TechnicianRepository technicians, IncidentRepository incidents, StrategyRegistry strategies) {
        this.technicians = technicians;
        this.incidents = incidents;
        this.strategies = strategies;
    }

    public int workloadOf(Technician t) { return incidents.countByAssignedTechnicianIdAndStatusIn(t.getId(), ACTIVE); }

    public List<TechnicianCandidate> candidates(Incident incident) {
        return technicians.findByActiveTrue().stream()
                .map(t -> new TechnicianCandidate(t, workloadOf(t),
                        incidents.countByAssignedTechnicianIdAndServiceId(t.getId(), incident.getService().getId())))
                .toList();
    }

    /** Runs the active strategy (or a named one). */
    public Technician choose(Incident incident, String strategyName) {
        AssignmentStrategy strategy = strategyName == null ? strategies.assignmentStrategy() : strategies.assignmentStrategy(strategyName);
        Optional<TechnicianCandidate> pick = strategy.select(incident, candidates(incident));
        return pick.map(TechnicianCandidate::technician).orElseThrow(() ->
                new TechnicianUnavailableException("No available technician found by strategy " + strategy.name()
                        + (incident.getCategory() != null ? " for category " + incident.getCategory().getName() : "")));
    }

    /** Validates a manual pick made by an admin. */
    public void validateManual(Technician t) {
        if (!t.isActive() || !t.isAvailable()) throw new TechnicianUnavailableException("Technician " + t.getFullName() + " is not available");
        int load = workloadOf(t);
        if (load >= t.getMaxWorkload()) throw new WorkloadExceededException(t.getFullName(), t.getMaxWorkload());
    }

    public record Recommendation(Technician technician, double score, int workload, boolean skillMatch) { }

    /** Ranked recommendation list for the admin UI (intelligent feature D). */
    public List<Recommendation> recommend(Incident incident) {
        SmartRecommendationStrategy smart = new SmartRecommendationStrategy();
        return candidates(incident).stream()
                .filter(c -> c.technician().isAvailable())
                .map(c -> new Recommendation(c.technician(), smart.score(incident, c), c.activeWorkload(), c.technician().hasSkill(incident.getCategory())))
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
                .toList();
    }
}
