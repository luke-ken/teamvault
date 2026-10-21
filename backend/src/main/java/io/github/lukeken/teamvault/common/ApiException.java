package io.github.lukeken.teamvault.common;

/**
 * Base of every exception the advice turns into a problem body. A throw site names the
 * kind of error; the status and the type URI follow from it.
 */
public class ApiException extends RuntimeException {

	private final ProblemType type;

	public ApiException(ProblemType type, String detail) {
		super(detail);
		this.type = type;
	}

	public ProblemType type() {
		return type;
	}

}
