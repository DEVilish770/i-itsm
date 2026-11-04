package edu.bits.itsm.event;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.IncidentStatus;

import java.time.LocalDateTime;

/**
 * Immutable event published on the {@link IncidentEventBus} (Observer pattern "subject" payload).
 * Carries the incident snapshot, who caused it, the state transition (if any) and a description.
 */
public record IncidentEvent(IncidentEventType type,
                            Incident incident,
                            String actor,
                            IncidentStatus previousStatus,
                            IncidentStatus newStatus,
                            String description,
                            LocalDateTime occurredAt) {

    public static IncidentEvent of(IncidentEventType type, Incident incident, String actor,
                                   IncidentStatus previous, String description) {
        return new IncidentEvent(type, incident, actor, previous, incident.getStatus(), description, LocalDateTime.now());
    }

    public static IncidentEvent of(IncidentEventType type, Incident incident, String actor, String description) {
        return new IncidentEvent(type, incident, actor, incident.getStatus(), incident.getStatus(), description, LocalDateTime.now());
    }
}
