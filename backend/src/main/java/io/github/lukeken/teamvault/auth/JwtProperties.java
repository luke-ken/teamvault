package io.github.lukeken.teamvault.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token settings (ADR-004 for the access token, ADR-007 for the refresh token). Bound
 * from {@code teamvault.jwt.*}; the secret arrives via {@code TEAMVAULT_JWT_SECRET}.
 * Validation runs at boot, so a missing or too-short secret stops the app before it can
 * sign a single token.
 *
 * @param secret HS256 key material; 32 characters is the 256-bit minimum the algorithm
 * needs
 * @param ttl access-token lifetime; short on purpose because there is no revocation
 * @param refreshTtl idle lifetime of one refresh token; each rotation starts it over
 * @param refreshFamilyTtl hard cap for a family, counted from the login that started it
 * @param refreshCookieSecure the Secure flag on the refresh cookie; off only in the dev
 * profile, where the API answers on plain http://localhost
 */
@Validated
@ConfigurationProperties("teamvault.jwt")
public record JwtProperties(
		@NotBlank(message = "TEAMVAULT_JWT_SECRET must be set") @Size(min = 32,
				message = "TEAMVAULT_JWT_SECRET must be at least 32 characters") String secret,
		@DefaultValue("15m") Duration ttl, @DefaultValue("14d") Duration refreshTtl,
		@DefaultValue("30d") Duration refreshFamilyTtl, @DefaultValue("true") boolean refreshCookieSecure) {
}
