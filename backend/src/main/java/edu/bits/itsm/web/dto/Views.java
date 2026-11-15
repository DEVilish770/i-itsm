package edu.bits.itsm.web.dto;

import edu.bits.itsm.domain.audit.AuditRecord;
import edu.bits.itsm.domain.incident.*;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.sla.SLATracker;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Read models returned by the API. Entities are never serialised directly:
 * this keeps the password hash and lazy associations out of responses (encapsulation at the boundary).
 */
public final class Views {
    private Views() { }

    public record UserView(Long id, String username, String fullName, String email, String phone, String role, boolean active,
                           String department, Set<String> skills, Integer experienceYears, Integer maxWorkload,
                           Boolean available, Boolean teamLead, Integer activeWorkload) {
        public static UserView of(User u, Integer workload) {
            if (u instanceof Technician t) {
                return new UserView(t.getId(), t.getUsername(), t.getFullName(), t.getEmail(), t.getPhone(), t.getRole().name(), t.isActive(),
                        null, t.getSkills().stream().map(IncidentCategory::getName).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)),
                        t.getExperienceYears(), t.getMaxWorkload(), t.isAvailable(), t.isTeamLead(), workload);
            }
            String dept = u instanceof edu.bits.itsm.domain.user.Employee e ? e.getDepartment() : null;
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.isActive(),
                    dept, null, null, null, null, null, null);
        }
        public static UserView of(User u) { return of(u, null); }
    }

    public record Ref(Long id, String name) {
        public static Ref of(User u) { return u == null ? null : new Ref(u.getId(), u.getFullName()); }
        public static Ref of(ITService s) { return s == null ? null : new Ref(s.getId(), s.getName()); }
        public static Ref of(IncidentCategory c) { return c == null ? null : new Ref(c.getId(), c.getName()); }
    }

    public record SlaView(LocalDateTime startedAt, LocalDateTime responseDeadline, LocalDateTime resolutionDeadline,
                          LocalDateTime respondedAt, LocalDateTime resolvedAt, boolean responseViolated, boolean resolutionViolated,
                          String status, long minutesRemaining, double percentRemaining) {
        public static SlaView of(SLATracker s) {
            LocalDateTime now = LocalDateTime.now();
            return new SlaView(s.getStartedAt(), s.getResponseDeadline(), s.getResolutionDeadline(), s.getRespondedAt(), s.getResolvedAt(),
                    s.isResponseViolated(), s.isResolutionViolated(), s.getStatus().name(),
                    s.getResolutionDeadline() == null ? 0 : s.minutesRemainingToResolution(now), Math.round(s.percentRemaining(now)));
        }
    }

    public record NoteView(Long id, Ref author, String authorRole, String type, String content, LocalDateTime createdAt) {
        public static NoteView of(InvestigationNote n) {
            return new NoteView(n.getId(), Ref.of(n.getAuthor()), n.getAuthor().getRole().name(), n.getType().name(), n.getContent(), n.getCreatedAt());
        }
    }

    public record ResolutionView(String summary, String rootCause, String resolvedBy, LocalDateTime resolvedAt) {
        public static ResolutionView of(Resolution r) {
            return r == null || !r.isPresent() ? null : new ResolutionView(r.getSummary(), r.getRootCause(), r.getResolvedBy(), r.getResolvedAt());
        }
    }

    public record FeedbackView(Integer rating, String comment, LocalDateTime givenAt) {
        public static FeedbackView of(Feedback f) { return f == null || !f.isPresent() ? null : new FeedbackView(f.getRating(), f.getComment(), f.getGivenAt()); }
    }

    public record IncidentSummary(Long id, String incidentNumber, String title, String status, String priority, String impact, String urgency,
                                  Ref service, Ref category, Ref reporter, Ref technician, String slaStatus, LocalDateTime resolutionDeadline,
                                  int escalationLevel, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static IncidentSummary of(Incident i) {
            return new IncidentSummary(i.getId(), i.getIncidentNumber(), i.getTitle(), i.getStatus().name(),
                    i.getPriority() == null ? null : i.getPriority().name(), i.getImpact().name(), i.getUrgency().name(),
                    Ref.of(i.getService()), Ref.of(i.getCategory()), Ref.of(i.getReporter()), Ref.of(i.getAssignedTechnician()),
                    i.getSla().getStatus().name(), i.getSla().getResolutionDeadline(), i.getEscalationLevel(), i.getCreatedAt(), i.getUpdatedAt());
        }
    }

    public record IncidentDetail(Long id, String incidentNumber, String title, String description, String status, String priority,
                                 Integer priorityScore, String priorityRationale, String impact, String urgency, int affectedUsers,
                                 Ref service, Ref category, Ref reporter, Ref technician, SlaView sla, List<NoteView> notes,
                                 ResolutionView resolution, FeedbackView feedback, String relatedIncidentNumber, int escalationLevel,
                                 Set<String> allowedActions, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime acknowledgedAt,
                                 LocalDateTime closedAt, Ref closedBy, String closureNotes) {
        public static IncidentDetail of(Incident i) {
            return new IncidentDetail(i.getId(), i.getIncidentNumber(), i.getTitle(), i.getDescription(), i.getStatus().name(),
                    i.getPriority() == null ? null : i.getPriority().name(), i.getPriorityScore(), i.getPriorityRationale(),
                    i.getImpact().name(), i.getUrgency().name(), i.getAffectedUsers(),
                    Ref.of(i.getService()), Ref.of(i.getCategory()), Ref.of(i.getReporter()), Ref.of(i.getAssignedTechnician()),
                    SlaView.of(i.getSla()), i.getNotes().stream().map(NoteView::of).toList(),
                    ResolutionView.of(i.getResolution()), FeedbackView.of(i.getFeedback()),
                    i.getRelatedIncident() == null ? null : i.getRelatedIncident().getIncidentNumber(), i.getEscalationLevel(),
                    i.allowedActions(), i.getCreatedAt(), i.getUpdatedAt(), i.getAcknowledgedAt(), i.getClosedAt(), Ref.of(i.getClosedBy()), i.getClosureNotes());
        }
    }

    public record AuditView(Long id, LocalDateTime timestamp, Long incidentId, String incidentNumber, String actor, String action,
                            String previousState, String newState, String description) {
        public static AuditView of(AuditRecord a) {
            return new AuditView(a.getId(), a.getTimestamp(), a.getIncidentId(), a.getIncidentNumber(), a.getActor(), a.getAction(),
                    a.getPreviousState(), a.getNewState(), a.getDescription());
        }
    }

    /** {@code plainText} / {@code textSource} (AI or RULES) are set only on the reporter's plain-language updates, else null. */
    public record NotificationView(Long id, String channel, String subject, String body, Long incidentId, String eventType, boolean read, LocalDateTime sentAt, String recipient,
                                   String plainText, String textSource) {
        public static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getChannel().name(), n.getSubject(), n.getBody(), n.getIncidentId(), n.getEventType(), n.isRead(), n.getSentAt(), n.getRecipientAddress(),
                    n.getPlainText(), n.getTextSource() == null ? null : n.getTextSource().name());
        }
    }

    public record ServiceView(Long id, String name, String description, String criticality, String owner, int userBase, boolean active) {
        public static ServiceView of(ITService s) { return new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getCriticality().name(), s.getOwner(), s.getUserBase(), s.isActive()); }
    }

    public record CategoryView(Long id, String name, String description, String keywords, boolean active) {
        public static CategoryView of(IncidentCategory c) { return new CategoryView(c.getId(), c.getName(), c.getDescription(), c.getKeywords(), c.isActive()); }
    }
}
