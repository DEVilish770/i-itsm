package edu.bits.itsm.domain.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** IT manager / system administrator: configures the system and monitors everything. */
@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    protected Administrator() { }

    public Administrator(String username, String passwordHash, String fullName, String email, String phone) {
        super(username, passwordHash, fullName, email, phone);
    }

    @Override public Role getRole() { return Role.ADMIN; }
}
