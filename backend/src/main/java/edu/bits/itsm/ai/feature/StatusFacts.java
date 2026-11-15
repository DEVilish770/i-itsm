package edu.bits.itsm.ai.feature;

import edu.bits.itsm.domain.incident.Incident;
import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.event.IncidentEvent;
import edu.bits.itsm.event.IncidentEventType;

/**
 * The plain facts a reporter's update is written from, copied out of an event while its incident is still loaded,
 * so the background rewrite never touches a live entity. Priorities are already turned into everyday words.
 */
public record StatusFacts(IncidentEventType type, String incidentNumber, String title, String technician,
                          String urgency, String detail) {

    static StatusFacts of(IncidentEvent e) {
        Incident i = e.incident();
        String technician = i.getAssignedTechnician() == null ? null : i.getAssignedTechnician().getFullName();
        String detail = switch (e.type()) {
            case INFO_REQUESTED -> e.description();                                        // the technician's question
            case RESOLVED -> i.getResolution() == null ? null : i.getResolution().getSummary();
            default -> null;
        };
        return new StatusFacts(e.type(), i.getIncidentNumber(), i.getTitle(), technician, urgencyWords(i.getPriority()), detail);
    }

    /** RULES text: one clear sentence (two when IT support asked a question), with no service-desk jargon. */
    String template() {
        String issue = "\"" + title + "\" (" + incidentNumber + ")";
        String who = technician == null ? "IT support" : technician;
        return switch (type) {
            case ASSIGNED -> technician == null
                    ? "Your issue " + issue + " has been passed to IT support, who will look into it."
                    : "Your issue " + issue + " has been given to " + technician + " from IT support, who will look into it.";
            case ACKNOWLEDGED -> who + " has seen your issue " + issue + " and is working on it now.";
            case INFO_REQUESTED -> "IT support has a question about your issue " + issue
                    + (detail == null || detail.isBlank() ? "." : ": \"" + detail.strip() + "\"") + " Please reply on the incident page.";
            case RESOLVED -> "Your issue " + issue + " has been fixed. Please check that everything works and confirm it on the incident page.";
            default -> throw new IllegalArgumentException("No plain-language update for " + type);
        };
    }

    /** The facts as the user turn of the prompt (data, not instructions). */
    String promptText() {
        String happened = switch (type) {
            case ASSIGNED -> "A technician from IT support has been given the issue.";
            case ACKNOWLEDGED -> "The technician has seen the issue and started working on it.";
            case INFO_REQUESTED -> "The technician needs more information from the employee before going on.";
            case RESOLVED -> "The technician says the issue is fixed; the employee should check it and confirm.";
            default -> type.name();
        };
        StringBuilder s = new StringBuilder()
                .append("What just happened: ").append(happened)
                .append("\nIssue title: ").append(title)
                .append("\nIncident number: ").append(incidentNumber)
                .append("\nHow urgent: ").append(urgency);
        if (technician != null) s.append("\nTechnician: ").append(technician);
        if (detail != null && !detail.isBlank())
            s.append(type == IncidentEventType.INFO_REQUESTED ? "\nThe technician's question: " : "\nHow it was fixed (technician's note): ").append(detail.strip());
        return s.toString();
    }

    static String urgencyWords(Priority p) {
        if (p == null) return "not rated yet";
        return switch (p) {
            case P1 -> "very urgent";
            case P2 -> "urgent";
            case P3 -> "normal";
            case P4 -> "not urgent";
        };
    }
}
