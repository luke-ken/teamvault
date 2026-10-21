package io.github.lukeken.teamvault.common;

import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * The machine-readable error kinds of the API (ADR-005). The slug is contract: a client
 * switches on the type URI; the catalogue page behind it (docs/errors.md) is
 * documentation.
 */
public enum ProblemType {

	AUTHENTICATION_REQUIRED("authentication-required", HttpStatus.UNAUTHORIZED),
	INVALID_TOKEN("invalid-token", HttpStatus.UNAUTHORIZED),
	BAD_CREDENTIALS("bad-credentials", HttpStatus.UNAUTHORIZED),
	MEMBERSHIP_REQUIRED("membership-required", HttpStatus.FORBIDDEN),
	ADMIN_ROLE_REQUIRED("admin-role-required", HttpStatus.FORBIDDEN), FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),
	NOT_FOUND("not-found", HttpStatus.NOT_FOUND), ALREADY_EXISTS("already-exists", HttpStatus.CONFLICT),
	VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),
	MALFORMED_BODY("malformed-body", HttpStatus.BAD_REQUEST),
	MALFORMED_PARAMETER("malformed-parameter", HttpStatus.BAD_REQUEST),
	CONTENT_TOO_LARGE("content-too-large", HttpStatus.CONTENT_TOO_LARGE);

	private static final String CATALOGUE = "https://github.com/luke-ken/teamvault/blob/main/docs/errors.md#";

	private final String slug;

	private final HttpStatus status;

	ProblemType(String slug, HttpStatus status) {
		this.slug = slug;
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

	public URI uri() {
		return URI.create(CATALOGUE + slug);
	}

}
