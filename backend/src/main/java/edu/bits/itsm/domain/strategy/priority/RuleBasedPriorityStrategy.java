package edu.bits.itsm.domain.strategy.priority;

import edu.bits.itsm.domain.incident.Impact;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.incident.Urgency;
import edu.bits.itsm.domain.service.BusinessCriticality;

/**
 * Alternative, explainable rule table (first matching rule wins):
 * <ol>
 *   <li>Mission-critical service AND (impact ≥ HIGH OR urgency ≥ HIGH) → P1</li>
 *   <li>impact CRITICAL OR urgency CRITICAL → P1</li>
 *   <li>impact ≥ HIGH AND urgency ≥ MEDIUM → P2</li>
 *   <li>affected users ≥ 50 → P2</li>
 *   <li>impact ≥ MEDIUM OR urgency ≥ MEDIUM → P3</li>
 *   <li>otherwise → P4</li>
 * </ol>
 */
public class RuleBasedPriorityStrategy implements PriorityStrategy {

    @Override public String name() { return "RULE_BASED"; }
    @Override public String description() { return "Ordered rule table (first match wins)"; }

    @Override
    public PriorityResult calculate(PriorityContext c) {
        boolean critSvc = c.criticality() == BusinessCriticality.MISSION_CRITICAL;
        boolean impHigh = c.impact().compareTo(Impact.HIGH) >= 0;
        boolean urgHigh = c.urgency().compareTo(Urgency.HIGH) >= 0;
        boolean impMed = c.impact().compareTo(Impact.MEDIUM) >= 0;
        boolean urgMed = c.urgency().compareTo(Urgency.MEDIUM) >= 0;

        Priority p; String rule;
        if (critSvc && (impHigh || urgHigh))                       { p = Priority.P1; rule = "R1 mission-critical service with high impact/urgency"; }
        else if (c.impact() == Impact.CRITICAL || c.urgency() == Urgency.CRITICAL) { p = Priority.P1; rule = "R2 critical impact or urgency"; }
        else if (impHigh && urgMed)                                { p = Priority.P2; rule = "R3 high impact & at least medium urgency"; }
        else if (c.affectedUsers() >= 50)                          { p = Priority.P2; rule = "R4 ≥50 affected users"; }
        else if (impMed || urgMed)                                 { p = Priority.P3; rule = "R5 medium impact or urgency"; }
        else                                                       { p = Priority.P4; rule = "R6 default"; }

        int score = 5 - p.ordinal(); // P1=4 ... P4=1 for comparability
        return new PriorityResult(p, score, name() + ": " + rule + " → " + p);
    }
}
