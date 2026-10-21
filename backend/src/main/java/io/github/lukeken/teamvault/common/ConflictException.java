package io.github.lukeken.teamvault.common;

public class ConflictException extends ApiException {

	public ConflictException(String message) {
		super(ProblemType.ALREADY_EXISTS, message);
	}

}
