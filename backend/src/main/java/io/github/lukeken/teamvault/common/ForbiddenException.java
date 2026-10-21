package io.github.lukeken.teamvault.common;

public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(ProblemType.FORBIDDEN, message);
	}

	public ForbiddenException(ProblemType type, String message) {
		super(type, message);
	}

}
