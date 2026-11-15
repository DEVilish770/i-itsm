package edu.bits.itsm.notification;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.event.IncidentEvent;

/**
 * <b>Factory pattern</b>: turns a domain event into the subject/body text of a notification.
 * Centralises wording so every channel says the same thing.
 */
public final class NotificationFactory {

    private NotificationFactory() { }

    /** Subject and body for every channel, plus an optional plain-language line for the reporter (null = none). */
    public record Message(String subject, String body, String plainText) {
        public Message(String subject, String body) { this(subject, body, null); }
        public Message withPlainText(String text) { return new Message(subject, body, text); }
    }

    public static Message forEvent(IncidentEvent e) {
        Incident i = e.incident();
        String ref = i.getIncidentNumber() + " \"" + i.getTitle() + "\"";
        return switch (e.type()) {
            case CREATED          -> new Message("Incident " + i.getIncidentNumber() + " created", "Your incident " + ref + " has been logged with priority " + i.getPriority() + ".");
            case ASSIGNED         -> new Message(i.getIncidentNumber() + " assigned to " + techName(i), "Incident " + ref + " (" + i.getPriority() + ") was assigned to " + techName(i) + ".");
            case PRIORITY_CHANGED -> new Message(i.getIncidentNumber() + " priority is now " + i.getPriority(), e.description());
            case ACKNOWLEDGED     -> new Message(i.getIncidentNumber() + " acknowledged", techName(i) + " has acknowledged " + ref + ".");
            case INFO_REQUESTED   -> new Message("More information needed on " + i.getIncidentNumber(), e.description());
            case INFO_PROVIDED    -> new Message("Reporter replied on " + i.getIncidentNumber(), e.description());
            case RESOLVED         -> new Message(i.getIncidentNumber() + " resolved", "Incident " + ref + " has been resolved. Please verify the fix.");
            case VERIFIED         -> new Message(i.getIncidentNumber() + " verified by reporter", "The reporter confirmed the resolution of " + ref + ".");
            case REOPENED         -> new Message(i.getIncidentNumber() + " reopened", "The reporter reopened " + ref + ": the issue persists.");
            case CLOSED           -> new Message(i.getIncidentNumber() + " closed", "Incident " + ref + " is now closed. " + nvl(i.getClosureNotes()));
            case SLA_WARNING      -> new Message("SLA warning: " + i.getIncidentNumber(), e.description());
            case SLA_VIOLATED     -> new Message("SLA VIOLATED: " + i.getIncidentNumber(), e.description());
            case ESCALATED        -> new Message("Escalation: " + i.getIncidentNumber(), e.description());
            case DUPLICATE_DETECTED -> new Message("Possible duplicate: " + i.getIncidentNumber(), e.description());
            default               -> new Message(i.getIncidentNumber() + ": " + e.type(), nvl(e.description()));
        };
    }

    private static String techName(Incident i) {
        return i.getAssignedTechnician() == null ? "unassigned" : i.getAssignedTechnician().getFullName();
    }
    private static String nvl(String s) { return s == null ? "" : s; }
}
