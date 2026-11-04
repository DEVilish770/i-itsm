package edu.bits.itsm.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** TC-22, TC-23: text similarity used by duplicate detection. */
class DuplicateDetectionTest {

    @Test @DisplayName("TC-22 Tokeniser drops stop-words and stems so 'failing'/'failed'/'fails' collide")
    void tokenise() {
        Set<String> t = DuplicateDetectionService.tokens("Payment transactions are failing, cannot make payment!");
        assertTrue(t.contains("payment"));
        assertTrue(t.contains("fail"));
        assertFalse(t.contains("are"));
        assertFalse(t.contains("cannot"));
        assertEquals(DuplicateDetectionService.tokens("failed"), DuplicateDetectionService.tokens("fails"));
    }

    @Test @DisplayName("TC-23 Similar reports score high; unrelated reports score ~0; identical = 1")
    void similarity() {
        Set<String> a = DuplicateDetectionService.tokens("Payment application unavailable, checkout shows 500 error");
        Set<String> b = DuplicateDetectionService.tokens("Payment transaction failing, checkout page gives error 500");
        Set<String> c = DuplicateDetectionService.tokens("Printer on floor 3 out of toner");
        assertTrue(DuplicateDetectionService.jaccard(a, b) >= 0.25, "should be flagged as likely duplicate");
        assertEquals(0.0, DuplicateDetectionService.jaccard(a, c));
        assertEquals(1.0, DuplicateDetectionService.jaccard(a, a));
        assertEquals(0.0, DuplicateDetectionService.jaccard(Set.of(), a), "empty input never matches");
    }
}
