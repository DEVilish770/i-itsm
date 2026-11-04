package edu.bits.itsm.domain.user;

import edu.bits.itsm.domain.incident.IncidentCategory;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Support engineer. Holds the attributes the assignment strategies reason about:
 * skills (categories they can handle), seniority, availability and capacity.
 * <p>
 * <b>Association</b>: many-to-many with {@link IncidentCategory} (skills).
 * The technician is the inverse side of the Incident→Technician association,
 * but we do <i>not</i> hold a collection of incidents here; workload is a
 * query, which avoids loading hundreds of incidents just to count them.
 */
@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "technician_skills",
               joinColumns = @JoinColumn(name = "technician_id"),
               inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<IncidentCategory> skills = new HashSet<>();

    @Column(name = "experience_years")
    private int experienceYears;

    @Column(name = "max_workload")
    private int maxWorkload = 5;

    @Column(name = "available")
    private boolean available = true;

    @Column(name = "is_team_lead")
    private boolean teamLead = false;

    protected Technician() { }

    public Technician(String username, String passwordHash, String fullName, String email, String phone,
                      int experienceYears, int maxWorkload) {
        super(username, passwordHash, fullName, email, phone);
        this.experienceYears = experienceYears;
        this.maxWorkload = maxWorkload;
    }

    @Override public Role getRole() { return Role.TECHNICIAN; }

    public boolean hasSkill(IncidentCategory category) {
        return category != null && skills.stream().anyMatch(s -> s.getId().equals(category.getId()));
    }

    public Set<IncidentCategory> getSkills() { return skills; }
    public void setSkills(Set<IncidentCategory> skills) { this.skills = skills == null ? new HashSet<>() : skills; }
    public void addSkill(IncidentCategory c) { skills.add(c); }

    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = Math.max(0, experienceYears); }

    public int getMaxWorkload() { return maxWorkload; }
    public void setMaxWorkload(int maxWorkload) {
        if (maxWorkload < 1) throw new IllegalArgumentException("maxWorkload must be >= 1");
        this.maxWorkload = maxWorkload;
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isTeamLead() { return teamLead; }
    public void setTeamLead(boolean teamLead) { this.teamLead = teamLead; }
}
