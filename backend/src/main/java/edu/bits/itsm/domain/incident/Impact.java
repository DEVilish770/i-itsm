package edu.bits.itsm.domain.incident;

/** How widely the incident affects the organisation. Carries a numeric weight for scoring. */
public enum Impact {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    private final int weight;
    Impact(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
