package io.github.lukeken.teamvault.common;

/**
 * 401 from our own code. The security chain's two 401s (no token, rejected bearer) never
 * come through here; this one is for credentials the application checks itself, such as
 * the refresh token cookie (ADR-007).
 */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException(ProblemType type, String message) {
		super(type, message);
	}

}
