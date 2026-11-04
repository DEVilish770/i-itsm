package edu.bits.itsm.domain.strategy.priority;

/**
 * <b>Strategy pattern</b>: interchangeable algorithms for deciding incident priority.
 * The active strategy is chosen at runtime by the administrator.
 */
public interface PriorityStrategy {
    String name();
    String description();
    PriorityResult calculate(PriorityContext ctx);
}
