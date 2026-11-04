package edu.bits.itsm.domain.strategy.assignment;

import edu.bits.itsm.domain.user.Technician;

/** A technician together with the live data the strategies need (workload, prior experience on the service). */
public record TechnicianCandidate(Technician technician, int activeWorkload, int priorIncidentsOnService) {
    public boolean hasCapacity() { return activeWorkload < technician.getMaxWorkload(); }
}
