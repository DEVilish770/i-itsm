package edu.bits.itsm.domain.strategy.priority;

import edu.bits.itsm.domain.incident.Priority;

/** Output of a priority strategy: the priority, its numeric score and a human-readable rationale (documented requirement). */
public record PriorityResult(Priority priority, int score, String rationale) { }
