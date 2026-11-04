package edu.bits.itsm.service;

import edu.bits.itsm.domain.user.Employee;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.exception.ConflictException;
import edu.bits.itsm.exception.InvalidCredentialsException;
import edu.bits.itsm.repository.UserRepository;
import edu.bits.itsm.security.JwtService;
import edu.bits.itsm.web.dto.AuthRequests.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration (employees self-register) and login. Passwords are BCrypt-hashed; never stored or returned in clear. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
    }

    @Transactional
    public TokenResponse register(Register r) {
        if (users.existsByUsername(r.username())) throw new ConflictException("Username already taken");
        if (users.existsByEmail(r.email())) throw new ConflictException("Email already registered");
        Employee e = new Employee(r.username(), encoder.encode(r.password()), r.fullName(), r.email(), r.phone(), r.department());
        users.save(e);
        return toToken(e);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(Login l) {
        User u = users.findByUsername(l.username()).orElseThrow(InvalidCredentialsException::new);
        if (!u.isActive() || !encoder.matches(l.password(), u.getPasswordHash())) throw new InvalidCredentialsException();
        return toToken(u);
    }

    private TokenResponse toToken(User u) {
        return new TokenResponse(jwt.issue(u.getUsername(), u.getRole().name(), u.getId()), u.getId(), u.getUsername(), u.getFullName(), u.getRole().name());
    }
}
