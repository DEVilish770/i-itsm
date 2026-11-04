package edu.bits.itsm.domain.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Immutable history entry. Written by the AuditObserver for every important domain event. */
@Entity
@Table(name = "audit_records", indexes = @Index(name = "idx_audit_incident", columnList = "incident_id"))
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "incident_number", length = 20)
    private String incidentNumber;

    @Column(name = "actor", nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "previous_state", length = 30)
    private String previousState;

    @Column(name = "new_state", length = 30)
    private String newState;

    @Column(length = 1000)
    private String description;

    protected AuditRecord() { }

    public AuditRecord(Long incidentId, String incidentNumber, String actor, String action,
                       String previousState, String newState, String description) {
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.actor = actor;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.description = description;
    }

    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Long getIncidentId() { return incidentId; }
    public String getIncidentNumber() { return incidentNumber; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public String getDescription() { return description; }
}
