package io.github.lukeken.teamvault.common;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/**
 * Builds and decorates problem bodies (ADR-005). The one place that knows the extension
 * members, so the body shape has a single owner.
 */
final class Problems {

	static final String REQUEST_ID = "requestId";

	private Problems() {
	}

	static ProblemDetail of(ProblemType type, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
		problem.setType(type.uri());
		problem.setTitle(type.status().getReasonPhrase());
		return decorate(problem);
	}

	static ProblemDetail decorate(ProblemDetail problem) {
		String requestId = MDC.get(RequestIdFilter.MDC_KEY);
		if (requestId != null) {
			problem.setProperty(REQUEST_ID, requestId);
		}
		return problem;
	}

}
