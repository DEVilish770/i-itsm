package edu.bits.itsm.domain.incident;

import edu.bits.itsm.domain.service.ITService;
import edu.bits.itsm.domain.sla.SLATracker;
import edu.bits.itsm.domain.user.Technician;
import edu.bits.itsm.domain.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root of the domain: an IT incident and everything that belongs to it.
 * <p>
 * <b>Encapsulation</b>: every state change goes through a behavioural method
 * ({@link #acknowledge()}, {@link #resolve(Resolution)}, ...) which delegates
 * to the current {@link IncidentState}. There is no public {@code setStatus};
 * the only way to move the lifecycle forward is through legal transitions.
 * <p>
 * <b>Composition</b>: {@link SLATracker}, {@link Resolution}, {@link Feedback}
 * are embedded value objects; {@link InvestigationNote}s are owned children.
 * <b>Association</b>: reporter, assigned technician, category and service are
 * references to independent entities.
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_priority", columnList = "priority"),
        @Index(name = "idx_incident_service", columnList = "service_id"),
        @Index(name = "idx_incident_technician", columnList = "technician_id")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-friendly identifier, e.g. INC-000123. Assigned by the service layer after the first save. */
    @Column(name = "incident_number", unique = true, length = 20)
    private String incidentNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private IncidentCategory category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "service_id")
    private ITService service;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Impact impact;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Urgency urgency;

    @Enumerated(EnumType.STRING) @Column(length = 5)
    private Priority priority;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Column(name = "priority_rationale", length = 500)
    private String priorityRationale;

    @Column(name = "affected_users")
    private int affectedUsers;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private Technician assignedTechnician;

    @Embedded
    private SLATracker sla = new SLATracker();

    @Embedded
    private Resolution resolution;

    @Embedded
    private Feedback feedback;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<InvestigationNote> notes = new ArrayList<>();

    /** If this incident was flagged as a duplicate/related of an earlier one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_incident_id")
    private Incident relatedIncident;

    @Column(name = "escalation_level")
    private int escalationLevel = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    @Column(name = "closure_notes", length = 1000)
    private String closureNotes;

    protected Incident() { }

    public Incident(String title, String description, User reporter, ITService service,
                    Impact impact, Urgency urgency, int affectedUsers) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Description is required");
        this.title = title.trim();
        this.description = description.trim();
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.service = Objects.requireNonNull(service, "service");
        this.impact = Objects.requireNonNull(impact, "impact");
        this.urgency = Objects.requireNonNull(urgency, "urgency");
        this.affectedUsers = Math.max(0, affectedUsers);
    }

    // ------------------------------------------------------------ State pattern delegation

    private IncidentState state() { return IncidentStates.of(status); }

    public Set<String> allowedActions() { return state().allowedActions(); }

    /** Attach classification + priority result and move NEW → CLASSIFIED. */
    public void classify(IncidentCategory category, Priority priority, int score, String rationale) {
        this.category = Objects.requireNonNull(category);
        this.priority = Objects.requireNonNull(priority);
        this.priorityScore = score;
        this.priorityRationale = rationale;
        state().classify(this);
    }

    public void assign(Technician technician)          { state().assign(this, Objects.requireNonNull(technician)); }
    public void acknowledge()                          { state().acknowledge(this); }
    public void startInvestigation()                   { state().startInvestigation(this); }
    public void requestInfo()                          { state().requestInfo(this); }
    public void provideInfo()                          { state().provideInfo(this); }
    public void resolve(Resolution resolution)         { state().resolve(this, Objects.requireNonNull(resolution)); }
    public void verify()                               { state().verify(this); }
    public void reopen()                               { state().reopen(this); }
    public void close(User by, String notes)           { state().close(this, by, notes); }

    // ------------------------------------------------------------ package-private mutators used by states

    void moveTo(IncidentStatus next) { this.status = next; touch(); }
    void attachTechnician(Technician t) { this.assignedTechnician = t; touch(); }
    void attachResolution(Resolution r) { this.resolution = r; touch(); }
    void setAcknowledgedAt(LocalDateTime at) { this.acknowledgedAt = at; }
    void recordClosure(User by, String notes) { this.closedBy = by; this.closureNotes = notes; this.closedAt = LocalDateTime.now(); }

    // ------------------------------------------------------------ other behaviour

    public InvestigationNote addNote(User author, NoteType type, String content) {
        InvestigationNote n = new InvestigationNote(this, author, type, content);
        notes.add(n);
        touch();
        return n;
    }

    public void giveFeedback(int rating, String comment) {
        if (status != IncidentStatus.RESOLVED && status != IncidentStatus.VERIFIED && status != IncidentStatus.CLOSED)
            throw new IllegalStateException("Feedback can only be given after resolution");
        this.feedback = new Feedback(rating, comment, LocalDateTime.now());
        touch();
    }

    /** Priority can be re-evaluated (e.g. by an admin or when impact changes) in any open state. */
    public void overridePriority(Priority newPriority, String rationale) {
        if (!status.isOpen()) throw new IllegalStateException("Cannot change priority of a resolved/closed incident");
        this.priority = Objects.requireNonNull(newPriority);
        this.priorityRationale = rationale;
        touch();
    }

    public void updateDetails(String title, String description, Impact impact, Urgency urgency, int affectedUsers) {
        if (!status.isOpen()) throw new IllegalStateException("Cannot edit a resolved/closed incident");
        if (title != null && !title.isBlank()) this.title = title.trim();
        if (description != null && !description.isBlank()) this.description = description.trim();
        if (impact != null) this.impact = impact;
        if (urgency != null) this.urgency = urgency;
        if (affectedUsers >= 0) this.affectedUsers = affectedUsers;
        touch();
    }

    public void escalate() { this.escalationLevel++; touch(); }
    public void markRelatedTo(Incident other) { this.relatedIncident = other; touch(); }
    public void assignNumber(String number) { if (this.incidentNumber == null) this.incidentNumber = number; }

    private void touch() { this.updatedAt = LocalDateTime.now(); }

    public boolean isAssignedTo(User user) {
        return assignedTechnician != null && user != null && Objects.equals(assignedTechnician.getId(), user.getId());
    }
    public boolean isReportedBy(User user) {
        return user != null && Objects.equals(reporter.getId(), user.getId());
    }

    // ------------------------------------------------------------ getters

    public Long getId() { return id; }
    public String getIncidentNumber() { return incidentNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getReporter() { return reporter; }
    public IncidentCategory getCategory() { return category; }
    public ITService getService() { return service; }
    public Impact getImpact() { return impact; }
    public Urgency getUrgency() { return urgency; }
    public Priority getPriority() { return priority; }
    public Integer getPriorityScore() { return priorityScore; }
    public String getPriorityRationale() { return priorityRationale; }
    public int getAffectedUsers() { return affectedUsers; }
    public IncidentStatus getStatus() { return status; }
    public Technician getAssignedTechnician() { return assignedTechnician; }
    public SLATracker getSla() { return sla; }
    public Resolution getResolution() { return resolution; }
    public Feedback getFeedback() { return feedback; }
    public List<InvestigationNote> getNotes() { return Collections.unmodifiableList(notes); }
    public Incident getRelatedIncident() { return relatedIncident; }
    public int getEscalationLevel() { return escalationLevel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public User getClosedBy() { return closedBy; }
    public String getClosureNotes() { return closureNotes; }

    @Override public boolean equals(Object o) { return this == o || (o instanceof Incident i && id != null && id.equals(i.id)); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return incidentNumber + " [" + status + "/" + priority + "] " + title; }
}
