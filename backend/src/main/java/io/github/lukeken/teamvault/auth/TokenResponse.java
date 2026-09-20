package io.github.lukeken.teamvault.auth;

import java.time.Instant;

public record TokenResponse(String token, Instant expiresAt) {
}
