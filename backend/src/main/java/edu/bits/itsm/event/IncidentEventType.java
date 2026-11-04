package edu.bits.itsm.event;

/** Every important thing that can happen to an incident. Observers switch on this. */
public enum IncidentEventType {
    CREATED, CLASSIFIED, PRIORITY_CHANGED, ASSIGNED, ACKNOWLEDGED, INVESTIGATION_STARTED,
    INFO_REQUESTED, INFO_PROVIDED, NOTE_ADDED, RESOLVED, VERIFIED, REOPENED, CLOSED,
    SLA_WARNING, SLA_VIOLATED, ESCALATED, DUPLICATE_DETECTED, FEEDBACK_GIVEN
}
