package io.github.lukeken.teamvault.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

/**
 * The one writer of error bodies (ADR-005). The base class turns Spring MVC's own
 * exceptions (unknown route, wrong method, unsupported media type, unreadable body,
 * validation, type mismatch, upload size) into Problem Details; all of them funnel
 * through handleExceptionInternal, overridden once to add the request id. Security-chain
 * failures arrive here too: JsonAuthenticationEntryPoint delegates them to the MVC
 * resolver.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	record FieldViolation(String field, String message) {
	}

	// --- framework exceptions: decorate what the base class built ---

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			Problems.decorate(problem);
		}
		return response;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.sorted(Comparator.comparing(FieldError::getField))
			.map(f -> new FieldViolation(f.getField(), String.valueOf(f.getDefaultMessage())))
			.toList();
		ProblemDetail problem = Problems.of(ProblemType.VALIDATION_FAILED, "Validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// Broken JSON or a value the type cannot take (an unknown tier, a word where a number
	// goes). The parser's message names internal classes, so it stays server-side.
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, Problems.of(ProblemType.MALFORMED_BODY, "Malformed request body"), headers,
				status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex,
				Problems.of(ProblemType.MALFORMED_PARAMETER, "Malformed value in request path or parameter"), headers,
				status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex,
				Problems.of(ProblemType.CONTENT_TOO_LARGE, "Uploaded file exceeds the size limit"), headers, status,
				request);
	}

	// --- our own exceptions ---

	@ExceptionHandler(ApiException.class)
	ProblemDetail apiException(ApiException e) {
		return Problems.of(e.type(), e.getMessage());
	}

	// Unique constraints are the last line of defence against races (two registrations of
	// the same email in the same millisecond). Generic message: the constraint name says
	// nothing a client should learn.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail constraintViolation(DataIntegrityViolationException e) {
		return Problems.of(ProblemType.ALREADY_EXISTS, "Resource already exists");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail illegalArgument(IllegalArgumentException e) {
		return Problems.of(ProblemType.MALFORMED_PARAMETER, e.getMessage());
	}

	// --- authentication: from the login controller and from the security chain ---

	// No token at all. The chain's entry point delegates here.
	@ExceptionHandler(InsufficientAuthenticationException.class)
	ResponseEntity<ProblemDetail> noToken(InsufficientAuthenticationException e) {
		return bearerChallenge(Problems.of(ProblemType.AUTHENTICATION_REQUIRED, "Authentication required"));
	}

	// Token present but rejected by the decoder: malformed, bad signature, expired.
	@ExceptionHandler(InvalidBearerTokenException.class)
	ResponseEntity<ProblemDetail> invalidToken(InvalidBearerTokenException e) {
		return bearerChallenge(Problems.of(ProblemType.INVALID_TOKEN, "Invalid or expired token"));
	}

	// Login failures reach the advice directly: the controller calls the manager itself.
	// One detail for unknown email and wrong password, no account enumeration.
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail badCredentials(AuthenticationException e) {
		return Problems.of(ProblemType.BAD_CREDENTIALS, "Invalid email or password");
	}

	// Catch-all: log the details server-side, never leak internals to the client (OWASP).
	// about:blank on purpose: the status is all the client is told.
	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception e) {
		log.error("Unhandled exception", e);
		return Problems
			.decorate(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
	}

	// RFC 6750: a 401 names the scheme the client should use.
	private ResponseEntity<ProblemDetail> bearerChallenge(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus()).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(problem);
	}

}
