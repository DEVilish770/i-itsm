package edu.bits.itsm.strategy;

import edu.bits.itsm.domain.incident.Impact;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.incident.Urgency;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.strategy.priority.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** TC-14 .. TC-16: priority algorithms (Strategy pattern) and their documented boundaries. */
class PriorityStrategyTest {

    private final PriorityStrategy weighted = new WeightedScoringPriorityStrategy();
    private final PriorityStrategy rules = new RuleBasedPriorityStrategy();

    @ParameterizedTest(name = "TC-14 weighted: {0}/{1}/{2} users={3} → {4}")
    @CsvSource({
            "CRITICAL, HIGH,   MISSION_CRITICAL, 400, P1",   // 48+8=56
            "HIGH,     HIGH,   MISSION_CRITICAL, 10,  P1",   // 36 boundary
            "HIGH,     MEDIUM, HIGH,             0,   P2",   // 18 boundary
            "MEDIUM,   MEDIUM, MEDIUM,           0,   P3",   // 8 boundary
            "LOW,      LOW,    LOW,              0,   P4",   // 1
            "LOW,      LOW,    LOW,              150, P3",   // 1+8=9 → bonus lifts P4 to P3
            "MEDIUM,   LOW,    MEDIUM,           30,  P3"    // 4+4=8 boundary
    })
    void weightedScoring(Impact i, Urgency u, BusinessCriticality c, int users, Priority expected) {
        PriorityResult r = weighted.calculate(new PriorityContext(i, u, c, users, 1000));
        assertEquals(expected, r.priority());
        assertTrue(r.rationale().contains("→ " + expected), "rationale must explain the result");
    }

    @ParameterizedTest(name = "TC-15 rule-based: {0}/{1}/{2} users={3} → {4}")
    @CsvSource({
            "HIGH,   LOW,    MISSION_CRITICAL, 0,  P1",   // R1
            "LOW,    CRITICAL, LOW,            0,  P1",   // R2
            "HIGH,   MEDIUM, MEDIUM,           0,  P2",   // R3
            "LOW,    LOW,    LOW,              50, P2",   // R4 boundary
            "LOW,    LOW,    LOW,              49, P4",   // just under R4 → default
            "MEDIUM, LOW,    LOW,              0,  P3"    // R5
    })
    void ruleBased(Impact i, Urgency u, BusinessCriticality c, int users, Priority expected) {
        assertEquals(expected, rules.calculate(new PriorityContext(i, u, c, users, 100)).priority());
    }

    @Test @DisplayName("TC-16 Strategies are interchangeable through the common interface")
    void interchangeable() {
        PriorityContext ctx = new PriorityContext(Impact.CRITICAL, Urgency.CRITICAL, BusinessCriticality.MISSION_CRITICAL, 0, 0);
        for (PriorityStrategy s : new PriorityStrategy[]{weighted, rules}) {
            assertEquals(Priority.P1, s.calculate(ctx).priority(), s.name());
            assertNotNull(s.name()); assertNotNull(s.description());
        }
    }
}
