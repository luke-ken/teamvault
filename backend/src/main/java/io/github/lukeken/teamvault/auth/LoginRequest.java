package io.github.lukeken.teamvault.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credentials for {@code POST /api/auth/login}. Validated before authentication runs. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {

    /** Never echo the password, even by accident in a log line. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
