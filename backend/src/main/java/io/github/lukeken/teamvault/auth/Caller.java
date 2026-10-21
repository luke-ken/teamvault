package io.github.lukeken.teamvault.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * ADR-004: sub carries the user UUID, set by TokenService and verified by the decoder.
 */
public final class Caller {

	private Caller() {
	}

	public static UUID id(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
