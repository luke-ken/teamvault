package io.github.lukeken.teamvault.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/register}. Validated before the service runs. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // 72 is the bcrypt input limit; longer passwords would be silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName
) {
    /** Never echo the password, even by accident in a log line. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
