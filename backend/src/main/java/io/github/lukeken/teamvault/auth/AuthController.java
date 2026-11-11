package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.user.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@SecurityRequirements // public: clears the global bearer requirement for this controller
@Tag(name = "auth")
public class AuthController {

	private final AuthService authService;

	private final RefreshCookie refreshCookie;

	public AuthController(AuthService authService, RefreshCookie refreshCookie) {
		this.authService = authService;
		this.refreshCookie = refreshCookie;
	}

	/**
	 * Creates the account only. The client logs in afterwards; tokens come from one
	 * place.
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	/**
	 * Access token in the body, refresh token in an httpOnly cookie (ADR-007).
	 * Non-browser clients read the body and ignore the cookie.
	 */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return withCookie(authService.login(request));
	}

	/**
	 * The cookie is the credential here, so the parameter is optional on purpose: a
	 * missing cookie is one of the 401 outcomes, not a 400.
	 */
	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(
			@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
		return withCookie(authService.refresh(refreshToken));
	}

	/** Revokes the family and clears the cookie. Idempotent: no cookie is still a 204. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString()).build();
	}

	private ResponseEntity<TokenResponse> withCookie(AuthTokens tokens) {
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.set(tokens.refreshToken()).toString())
			.body(tokens.access());
	}

}
