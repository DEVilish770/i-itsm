package edu.bits.itsm.event;

/** <b>Observer pattern</b>: anything that wants to react to incident events implements this. */
public interface IncidentObserver {
    void onIncidentEvent(IncidentEvent event);
}
