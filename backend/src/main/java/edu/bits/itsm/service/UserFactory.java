package edu.bits.itsm.service;

import edu.bits.itsm.domain.incident.IncidentCategory;
import edu.bits.itsm.domain.user.*;
import edu.bits.itsm.web.dto.AdminRequests.CreateUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** <b>Factory pattern</b>: builds the right User subclass for a role, hiding constructor differences from callers. */
@Component
public class UserFactory {

    private final PasswordEncoder encoder;

    public UserFactory(PasswordEncoder encoder) { this.encoder = encoder; }

    public User create(CreateUser r, Set<IncidentCategory> skills) {
        String hash = encoder.encode(r.password());
        return switch (r.role()) {
            case EMPLOYEE -> new Employee(r.username(), hash, r.fullName(), r.email(), r.phone(), r.department());
            case ADMIN -> new Administrator(r.username(), hash, r.fullName(), r.email(), r.phone());
            case TECHNICIAN -> {
                Technician t = new Technician(r.username(), hash, r.fullName(), r.email(), r.phone(),
                        r.experienceYears() == null ? 0 : r.experienceYears(), r.maxWorkload() == null ? 5 : r.maxWorkload());
                t.setSkills(skills);
                t.setTeamLead(Boolean.TRUE.equals(r.teamLead()));
                yield t;
            }
        };
    }
}
