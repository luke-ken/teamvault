package io.github.lukeken.teamvault.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	public record ApiError(int status, String error, String message) {
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> notFound(NotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<ApiError> forbidden(ForbiddenException e) {
		return error(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiError> conflict(ConflictException e) {
		return error(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<ApiError> badRequest(Exception e) {
		String message = e instanceof MethodArgumentTypeMismatchException
				? "Malformed value in request path or parameter"
				: e.getMessage();
		return error(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
		return error(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the size limit");
	}

	// Catch-all: log the details server-side, never leak internals to the client (OWASP).
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception e) {
		log.error("Unhandled exception", e);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

	private ResponseEntity<ApiError> error(HttpStatus status, String message) {
		return ResponseEntity.status(status)
				.body(new ApiError(status.value(), status.getReasonPhrase(), message));
	}
}
