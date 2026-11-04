package edu.bits.itsm.intelligence;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;
import edu.bits.itsm.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Intelligent feature A/B — duplicate & related-incident detection.
 * <p>
 * Algorithm: for open incidents on the same service created in the last N hours, compute the
 * Jaccard similarity of the token sets of (title + description) after stop-word removal and
 * light stemming. Scores ≥ {@code threshold} are reported as likely duplicates; anything on the
 * same service in the window is "related" (incident correlation).
 */
@Service
public class DuplicateDetectionService {

    private static final Set<String> STOP = Set.of("the","a","an","is","are","to","of","and","or","in","on","at","for",
            "with","not","it","i","we","my","our","this","that","be","can","cannot","cant","unable","please","help","app",
            "application","issue","problem","error","when","from","has","have","was","were","been","getting","get");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private final IncidentRepository incidents;
    private final double threshold;
    private final int windowHours;

    public DuplicateDetectionService(IncidentRepository incidents,
                                     @Value("${itsm.duplicate.threshold:0.25}") double threshold,
                                     @Value("${itsm.duplicate.window-hours:48}") int windowHours) {
        this.incidents = incidents;
        this.threshold = threshold;
        this.windowHours = windowHours;
    }

    public record Match(Incident incident, double similarity, boolean likelyDuplicate) { }

    /** Candidates for a not-yet-saved incident described by these fields. */
    public List<Match> findSimilar(Long serviceId, String title, String description, Long excludeId) {
        Set<String> tokens = tokens(title + " " + description);
        List<IncidentStatus> open = Arrays.stream(IncidentStatus.values()).filter(IncidentStatus::isOpen).toList();
        List<Match> out = new ArrayList<>();
        for (Incident other : incidents.findByServiceIdAndStatusInAndCreatedAtAfter(serviceId, open, LocalDateTime.now().minusHours(windowHours))) {
            if (excludeId != null && excludeId.equals(other.getId())) continue;
            double sim = jaccard(tokens, tokens(other.getTitle() + " " + other.getDescription()));
            out.add(new Match(other, sim, sim >= threshold));
        }
        out.sort(Comparator.comparingDouble(Match::similarity).reversed());
        return out;
    }

    public static Set<String> tokens(String text) {
        Set<String> s = new HashSet<>();
        if (text == null) return s;
        for (String w : NON_WORD.split(text.toLowerCase())) {
            if (w.length() < 3 || STOP.contains(w)) continue;
            s.add(stem(w));
        }
        return s;
    }

    /** Very light stemmer: strips common English suffixes so "failing"/"failed"/"fails" collide. */
    static String stem(String w) {
        for (String suf : new String[]{"ing", "ed", "es", "s"}) {
            if (w.length() > suf.length() + 2 && w.endsWith(suf)) return w.substring(0, w.length() - suf.length());
        }
        return w;
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a); inter.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        return (double) inter.size() / union.size();
    }

    public double threshold() { return threshold; }
}
