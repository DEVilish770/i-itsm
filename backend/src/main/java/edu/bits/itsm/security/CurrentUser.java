package edu.bits.itsm.security;

import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.exception.ResourceNotFoundException;
import edu.bits.itsm.exception.UnauthorizedOperationException;
import edu.bits.itsm.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Helper to fetch the currently authenticated domain user. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) { this.users = users; }

    public AuthenticatedUser principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthenticatedUser au))
            throw new UnauthorizedOperationException("Not authenticated");
        return au;
    }

    public User get() {
        AuthenticatedUser p = principal();
        return users.findById(p.id()).orElseThrow(() -> new ResourceNotFoundException("User", p.id()));
    }
}
