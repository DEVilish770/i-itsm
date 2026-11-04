package edu.bits.itsm.intelligence;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Intelligent feature G — suggests resolutions from similar, already-resolved incidents on the same service. */
@Service
public class ResolutionSuggestionService {

    private final IncidentRepository incidents;

    public ResolutionSuggestionService(IncidentRepository incidents) { this.incidents = incidents; }

    public record Suggestion(String incidentNumber, String title, String resolutionSummary, String rootCause, double similarity) { }

    public List<Suggestion> suggestFor(Incident incident) {
        Set<String> mine = DuplicateDetectionService.tokens(incident.getTitle() + " " + incident.getDescription());
        List<IncidentStatus> done = List.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
        return incidents.findTop50ByServiceIdAndStatusInOrderByUpdatedAtDesc(incident.getService().getId(), done).stream()
                .filter(o -> !o.getId().equals(incident.getId()) && o.getResolution() != null)
                .map(o -> new Suggestion(o.getIncidentNumber(), o.getTitle(), o.getResolution().getSummary(),
                        o.getResolution().getRootCause(),
                        DuplicateDetectionService.jaccard(mine, DuplicateDetectionService.tokens(o.getTitle() + " " + o.getDescription()))))
                .filter(s -> s.similarity() > 0.1)
                .sorted(Comparator.comparingDouble(Suggestion::similarity).reversed())
                .limit(5)
                .toList();
    }
}
