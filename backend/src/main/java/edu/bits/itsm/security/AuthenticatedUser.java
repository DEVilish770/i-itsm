package edu.bits.itsm.security;

import edu.bits.itsm.domain.user.Role;

/** Lightweight principal stored in the SecurityContext (no entity, no lazy-loading surprises). */
public record AuthenticatedUser(Long id, String username, Role role) { }
