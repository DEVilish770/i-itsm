package edu.bits.itsm.web;

import edu.bits.itsm.security.CurrentUser;
import edu.bits.itsm.service.AuthService;
import edu.bits.itsm.web.dto.AuthRequests.*;
import edu.bits.itsm.web.dto.Views.UserView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, CurrentUser currentUser) { this.auth = auth; this.currentUser = currentUser; }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody Register r) { return auth.register(r); }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody Login l) { return auth.login(l); }

    @GetMapping("/me")
    public UserView me() { return UserView.of(currentUser.get()); }
}
