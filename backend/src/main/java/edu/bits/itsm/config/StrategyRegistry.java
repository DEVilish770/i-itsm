package edu.bits.itsm.config;

import edu.bits.itsm.domain.strategy.assignment.*;
import edu.bits.itsm.domain.strategy.priority.PriorityStrategy;
import edu.bits.itsm.domain.strategy.priority.RuleBasedPriorityStrategy;
import edu.bits.itsm.domain.strategy.priority.WeightedScoringPriorityStrategy;
import edu.bits.itsm.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all available strategies and which one is active. Admin can switch at runtime via the API,
 * which is exactly the "change the assignment strategy without redesigning the application" requirement.
 */
@Component
public class StrategyRegistry {

    private final Map<String, AssignmentStrategy> assignment = new LinkedHashMap<>();
    private final Map<String, PriorityStrategy> priority = new LinkedHashMap<>();
    private volatile String activeAssignment = "SMART_RECOMMENDATION";
    private volatile String activePriority = "WEIGHTED_SCORING";

    public StrategyRegistry() {
        List.of(new SmartRecommendationStrategy(), new SkillBasedAssignmentStrategy(),
                new LoadBalancedAssignmentStrategy(), new RoundRobinAssignmentStrategy())
            .forEach(s -> assignment.put(s.name(), s));
        List.of(new WeightedScoringPriorityStrategy(), new RuleBasedPriorityStrategy())
            .forEach(s -> priority.put(s.name(), s));
    }

    public AssignmentStrategy assignmentStrategy() { return assignment.get(activeAssignment); }
    public PriorityStrategy priorityStrategy() { return priority.get(activePriority); }

    public AssignmentStrategy assignmentStrategy(String name) {
        AssignmentStrategy s = assignment.get(name);
        if (s == null) throw new ResourceNotFoundException("AssignmentStrategy", name);
        return s;
    }

    public void activateAssignment(String name) { assignmentStrategy(name); activeAssignment = name; }
    public void activatePriority(String name) {
        if (!priority.containsKey(name)) throw new ResourceNotFoundException("PriorityStrategy", name);
        activePriority = name;
    }

    public String activeAssignmentName() { return activeAssignment; }
    public String activePriorityName() { return activePriority; }
    public List<AssignmentStrategy> assignmentStrategies() { return List.copyOf(assignment.values()); }
    public List<PriorityStrategy> priorityStrategies() { return List.copyOf(priority.values()); }
}
