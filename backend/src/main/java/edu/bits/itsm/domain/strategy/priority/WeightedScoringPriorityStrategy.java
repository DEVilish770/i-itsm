package edu.bits.itsm.domain.strategy.priority;

import edu.bits.itsm.domain.incident.Priority;

/**
 * Default algorithm (documented in the report):
 * <pre>
 *   base  = impact(1-4) × urgency(1-4) × criticality(1-4)         → 1..64
 *   bonus = +8 if affected users ≥ 100, +4 if ≥ 25, +0 otherwise
 *   score = base + bonus                                           → 1..72
 *   P1 ≥ 36 | P2 ≥ 18 | P3 ≥ 8 | else P4
 * </pre>
 * Multiplication (rather than addition) makes a single CRITICAL dimension dominate,
 * which matches how ITIL-style impact/urgency matrices behave.
 */
public class WeightedScoringPriorityStrategy implements PriorityStrategy {

    @Override public String name() { return "WEIGHTED_SCORING"; }
    @Override public String description() { return "impact × urgency × criticality + affected-user bonus"; }

    @Override
    public PriorityResult calculate(PriorityContext c) {
        int base = c.impact().getWeight() * c.urgency().getWeight() * c.criticality().getWeight();
        int bonus = c.affectedUsers() >= 100 ? 8 : c.affectedUsers() >= 25 ? 4 : 0;
        int score = base + bonus;
        Priority p = score >= 36 ? Priority.P1 : score >= 18 ? Priority.P2 : score >= 8 ? Priority.P3 : Priority.P4;
        String rationale = String.format("%s: impact %s(%d) × urgency %s(%d) × criticality %s(%d) = %d, +%d users bonus → score %d → %s",
                name(), c.impact(), c.impact().getWeight(), c.urgency(), c.urgency().getWeight(),
                c.criticality(), c.criticality().getWeight(), base, bonus, score, p);
        return new PriorityResult(p, score, rationale);
    }
}
