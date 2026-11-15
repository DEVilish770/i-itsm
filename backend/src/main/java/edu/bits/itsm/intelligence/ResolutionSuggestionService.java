package edu.bits.itsm.intelligence;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Intelligent feature G — suggests resolutions from similar, already-resolved incidents on the same service. */
@Service
public class ResolutionSuggestionService {

    private final IncidentRepository incidents;

    public ResolutionSuggestionService(IncidentRepository incidents) { this.incidents = incidents; }

    public record Suggestion(String incidentNumber, String title, String resolutionSummary, String rootCause, double similarity) { }

    public List<Suggestion> suggestFor(Incident incident) { return suggestFor(incident, other -> true); }

    /** Same, limited to past incidents the caller may see (an employee only sees resolutions of their own incidents). */
    public List<Suggestion> suggestFor(Incident incident, Predicate<Incident> visible) {
        Set<String> mine = DuplicateDetectionService.tokens(incident.getTitle() + " " + incident.getDescription());
        List<IncidentStatus> done = List.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
        return incidents.findTop50ByServiceIdAndStatusInOrderByUpdatedAtDesc(incident.getService().getId(), done).stream()
                .filter(o -> !o.getId().equals(incident.getId()) && o.getResolution() != null && visible.test(o))
                .map(o -> new Suggestion(o.getIncidentNumber(), o.getTitle(), o.getResolution().getSummary(),
                        o.getResolution().getRootCause(),
                        DuplicateDetectionService.jaccard(mine, DuplicateDetectionService.tokens(o.getTitle() + " " + o.getDescription()))))
                .filter(s -> s.similarity() > 0.1)
                .sorted(Comparator.comparingDouble(Suggestion::similarity).reversed())
                .limit(5)
                .toList();
    }
}
