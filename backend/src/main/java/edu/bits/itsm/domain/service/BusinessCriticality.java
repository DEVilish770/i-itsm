package edu.bits.itsm.domain.service;

/** How important a service is to the business. Weight feeds the priority score. */
public enum BusinessCriticality {
    LOW(1), MEDIUM(2), HIGH(3), MISSION_CRITICAL(4);

    private final int weight;
    BusinessCriticality(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
