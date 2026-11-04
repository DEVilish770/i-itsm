package edu.bits.itsm.web.dto;

import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() { }

    public record Login(@NotBlank String username, @NotBlank String password) { }

    public record Register(@NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String username,
                           @NotBlank @Size(min = 8, max = 72) String password,
                           @NotBlank @Size(max = 120) String fullName,
                           @NotBlank @Email String email,
                           @Size(max = 30) String phone,
                           @Size(max = 80) String department) { }

    public record TokenResponse(String token, Long id, String username, String fullName, String role) { }
}
