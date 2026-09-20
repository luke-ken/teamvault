package io.github.lukeken.teamvault.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Self-issued JWT settings (ADR-004). Bound from {@code teamvault.jwt.*}; the secret
 * arrives via {@code TEAMVAULT_JWT_SECRET}. Validation runs at boot, so a missing or
 * too-short secret stops the app before it can sign a single token.
 *
 * @param secret HS256 key material; 32 characters is the 256-bit minimum the algorithm needs
 * @param ttl    access-token lifetime; short on purpose because there is no revocation
 */
@Validated
@ConfigurationProperties("teamvault.jwt")
public record JwtProperties(
		@NotBlank(message = "TEAMVAULT_JWT_SECRET must be set")
		@Size(min = 32, message = "TEAMVAULT_JWT_SECRET must be at least 32 characters")
		String secret,
		@DefaultValue("15m") Duration ttl) {
}
