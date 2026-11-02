package io.github.lukeken.teamvault.common;

/**
 * 400 for an input rule that bean validation cannot express, for example a rule about a
 * multipart part. The type names the rule, so a client can tell it from a validation
 * failure on a JSON body.
 */
public class BadRequestException extends ApiException {

	public BadRequestException(ProblemType type, String message) {
		super(type, message);
	}

}
