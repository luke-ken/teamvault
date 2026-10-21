package io.github.lukeken.teamvault.common;

public class NotFoundException extends ApiException {

	public NotFoundException(String message) {
		super(ProblemType.NOT_FOUND, message);
	}

}
