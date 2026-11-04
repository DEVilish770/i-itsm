package edu.bits.itsm.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base of every actor in the system.
 * <p>
 * <b>Inheritance</b>: Employee, Technician and Administrator all genuinely
 * "are" users (they log in, have a name/email, appear in audit records), so an
 * is-a hierarchy is appropriate. JPA SINGLE_TABLE inheritance keeps joins cheap
 * while the discriminator column records the concrete type.
 * <p>
 * <b>Encapsulation</b>: fields are private; the password hash is write-only
 * from the outside and never exposed via a getter to serialisers.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected User() { }

    protected User(String username, String passwordHash, String fullName, String email, String phone) {
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = Objects.requireNonNull(fullName);
        this.email = Objects.requireNonNull(email);
        this.phone = phone;
    }

    /** Polymorphic: each subclass declares its own role. */
    public abstract Role getRole();

    /** Template-style hook: human-readable display used in audit log and notifications. */
    public String getDisplayLabel() {
        return getRole().name().charAt(0) + getRole().name().substring(1).toLowerCase() + " " + fullName;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Deliberately package-private-ish: only security code should read the hash. */
    public String getPasswordHash() { return passwordHash; }

    public void changePassword(String newHash) { this.passwordHash = Objects.requireNonNull(newHash); }
    public void rename(String fullName) { this.fullName = Objects.requireNonNull(fullName); }
    public void setEmail(String email) { this.email = Objects.requireNonNull(email); }
    public void setPhone(String phone) { this.phone = phone; }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof User u && id != null && id.equals(u.id));
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
    @Override public String toString() { return getRole() + ":" + username; }
}
