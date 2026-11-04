package edu.bits.itsm.domain.incident;

/** How quickly the incident must be addressed. Carries a numeric weight for scoring. */
public enum Urgency {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    private final int weight;
    Urgency(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
