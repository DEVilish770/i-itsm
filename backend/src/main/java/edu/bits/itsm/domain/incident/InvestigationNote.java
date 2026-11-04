package edu.bits.itsm.domain.incident;

import edu.bits.itsm.domain.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A note on an incident (investigation step, request for information, reply).
 * <b>Composition</b>: owned by exactly one Incident (orphanRemoval), cannot exist without it.
 */
@Entity
@Table(name = "investigation_notes")
public class InvestigationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteType type;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected InvestigationNote() { }

    InvestigationNote(Incident incident, User author, NoteType type, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Note content is required");
        this.incident = incident;
        this.author = author;
        this.type = type;
        this.content = content;
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public User getAuthor() { return author; }
    public NoteType getType() { return type; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
