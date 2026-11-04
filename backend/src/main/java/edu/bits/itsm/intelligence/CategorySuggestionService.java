package edu.bits.itsm.intelligence;

import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.repository.IncidentCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Intelligent feature F — rule-based automatic categorisation.
 * Each category carries admin-editable keywords; the description is scored by keyword hits
 * (title hits count double). Returns ranked suggestions with a confidence in [0,1].
 */
@Service
public class CategorySuggestionService {

    private final IncidentCategoryRepository categories;

    public CategorySuggestionService(IncidentCategoryRepository categories) { this.categories = categories; }

    public record Suggestion(IncidentCategory category, double confidence, List<String> matchedKeywords) { }

    public List<Suggestion> suggest(String title, String description) {
        String t = (title == null ? "" : title).toLowerCase();
        String d = (description == null ? "" : description).toLowerCase();
        List<Suggestion> out = new ArrayList<>();
        int bestScore = 0;
        Map<IncidentCategory, int[]> scores = new LinkedHashMap<>();
        Map<IncidentCategory, List<String>> hits = new HashMap<>();

        for (IncidentCategory c : categories.findByActiveTrue()) {
            if (c.getKeywords() == null) continue;
            int score = 0; List<String> matched = new ArrayList<>();
            for (String kw : c.getKeywords().split(",")) {
                String k = kw.trim().toLowerCase();
                if (k.isEmpty()) continue;
                int s = (t.contains(k) ? 2 : 0) + (d.contains(k) ? 1 : 0);
                if (s > 0) { score += s; matched.add(k); }
            }
            if (score > 0) { scores.put(c, new int[]{score}); hits.put(c, matched); bestScore = Math.max(bestScore, score); }
        }
        int total = scores.values().stream().mapToInt(v -> v[0]).sum();
        for (var e : scores.entrySet()) {
            out.add(new Suggestion(e.getKey(), total == 0 ? 0 : (double) e.getValue()[0] / total, hits.get(e.getKey())));
        }
        out.sort(Comparator.comparingDouble(Suggestion::confidence).reversed());
        return out;
    }

    public Optional<IncidentCategory> best(String title, String description) {
        return suggest(title, description).stream().findFirst().map(Suggestion::category);
    }
}
