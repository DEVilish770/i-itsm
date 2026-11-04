package edu.bits.itsm.domain.service;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * A service in the IT catalogue (Payment App, Email, VPN, ...).
 * Business criticality is a key input to the priority engine.
 */
@Entity
@Table(name = "it_services")
public class ITService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessCriticality criticality;

    @Column(length = 100)
    private String owner;

    /** Approximate user base; used to refine priority when the reporter gives no count. */
    @Column(name = "user_base")
    private int userBase;

    @Column(nullable = false)
    private boolean active = true;

    protected ITService() { }

    public ITService(String name, String description, BusinessCriticality criticality, String owner, int userBase) {
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.criticality = Objects.requireNonNull(criticality);
        this.owner = owner;
        this.userBase = userBase;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BusinessCriticality getCriticality() { return criticality; }
    public String getOwner() { return owner; }
    public int getUserBase() { return userBase; }
    public boolean isActive() { return active; }

    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public void setDescription(String description) { this.description = description; }
    public void setCriticality(BusinessCriticality criticality) { this.criticality = Objects.requireNonNull(criticality); }
    public void setOwner(String owner) { this.owner = owner; }
    public void setUserBase(int userBase) { this.userBase = Math.max(0, userBase); }
    public void setActive(boolean active) { this.active = active; }

    @Override public boolean equals(Object o) { return this == o || (o instanceof ITService s && id != null && id.equals(s.id)); }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return name; }
}
