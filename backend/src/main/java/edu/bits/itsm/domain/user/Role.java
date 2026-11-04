package edu.bits.itsm.domain.user;

/** Security roles. Each concrete {@link User} subclass maps to exactly one role. */
public enum Role {
    EMPLOYEE, TECHNICIAN, ADMIN
}
