package io.github.lukeken.teamvault.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * 401 for requests that never reach a controller: no token, or a token the decoder
 * rejects. The security chain runs outside Spring MVC, so instead of writing a body by
 * hand this hands the exception to the MVC resolver and ApiExceptionHandler answers
 * (ADR-005). One writer of error bodies; the two 401 kinds and WWW-Authenticate live in
 * the advice.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver resolver;

	// Qualified on purpose: Boot's DefaultErrorAttributes is a HandlerExceptionResolver
	// too, and only the composite named handlerExceptionResolver reaches the advice.
	public JsonAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		resolver.resolveException(request, response, null, authException);
	}

}
