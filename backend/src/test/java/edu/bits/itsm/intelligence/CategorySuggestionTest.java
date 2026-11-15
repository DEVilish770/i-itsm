package edu.bits.itsm.intelligence;

import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.repository.IncidentCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TC-37, TC-38: keyword auto-categorisation (intelligent feature F) matches whole words only. */
class CategorySuggestionTest {

    private final CategorySuggestionService service = serviceWith(
            new IncidentCategory("SOFTWARE", "", "software,word,excel,application crash"),
            new IncidentCategory("NETWORK", "", "network,lan,vpn"),
            new IncidentCategory("AUTHENTICATION", "", "login,password,account locked"));

    private static CategorySuggestionService serviceWith(IncidentCategory... categories) {
        IncidentCategoryRepository repo = mock(IncidentCategoryRepository.class);
        when(repo.findByActiveTrue()).thenReturn(List.of(categories));
        return new CategorySuggestionService(repo);
    }

    private List<String> suggestedNames(String title, String description) {
        return service.suggest(title, description).stream().map(s -> s.category().getName()).toList();
    }

    @Test @DisplayName("TC-37 A keyword inside a longer word does not count: 'password' is not 'word', 'plan' is not 'lan'")
    void wholeWordsOnly() {
        assertEquals(List.of("AUTHENTICATION"), suggestedNames("Password expired", "Cannot log in since the migration plan went live"));
    }

    @Test @DisplayName("TC-38 Matching ignores case, and a multi-word keyword still matches across any whitespace")
    void caseAndMultiWordKeywords() {
        assertEquals(List.of("NETWORK"), suggestedNames("LAN down on floor 2", ""));
        CategorySuggestionService.Suggestion top = service.suggest("Excel issue", "Application\n  CRASH when saving").get(0);
        assertEquals("SOFTWARE", top.category().getName());
        assertEquals(List.of("excel", "application crash"), top.matchedKeywords());
    }
}
