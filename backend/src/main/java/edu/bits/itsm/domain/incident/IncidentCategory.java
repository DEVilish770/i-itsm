package edu.bits.itsm.domain.incident;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * Incident category (Hardware, Software, Network, ...).
 * Modelled as an entity rather than an enum so that administrators can add new
 * categories at runtime without touching code — satisfying the requirement that
 * "new categories can be introduced without major changes to unrelated parts".
 */
@Entity
@Table(name = "incident_categories")
public class IncidentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 255)
    private String description;

    /** Comma-separated keywords used by the rule-based auto-categoriser. */
    @Column(length = 500)
    private String keywords;

    @Column(nullable = false)
    private boolean active = true;

    protected IncidentCategory() { }

    public IncidentCategory(String name, String description, String keywords) {
        this.name = Objects.requireNonNull(name).trim().toUpperCase();
        this.description = description;
        this.keywords = keywords;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getKeywords() { return keywords; }
    public boolean isActive() { return active; }

    public void setDescription(String description) { this.description = description; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public void setActive(boolean active) { this.active = active; }

    @Override public boolean equals(Object o) { return this == o || (o instanceof IncidentCategory c && id != null && id.equals(c.id)); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return name; }
}
