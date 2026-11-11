package io.github.lukeken.teamvault.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The one place that knows how the refresh token travels (ADR-007). HttpOnly keeps it
 * from scripts, SameSite=Strict keeps it off cross-site requests, the path keeps it off
 * every endpoint but the auth ones, and Secure is a property so the dev profile can run
 * on plain http://localhost. The access token never goes into a cookie: it stays a bearer
 * header, so curl, the .http file and a browser all speak the same API.
 */
@Component
public class RefreshCookie {

	public static final String NAME = "refresh_token";

	/**
	 * Register and login ignore the cookie; refresh and logout need it to name the
	 * family.
	 */
	static final String PATH = "/api/auth";

	private final JwtProperties props;

	public RefreshCookie(JwtProperties props) {
		this.props = props;
	}

	public ResponseCookie set(String rawToken) {
		return builder(rawToken).maxAge(props.refreshTtl()).build();
	}

	/**
	 * Same name, path and flags, otherwise the browser treats it as a different cookie.
	 */
	public ResponseCookie clear() {
		return builder("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder builder(String value) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(props.refreshCookieSecure())
			.sameSite("Strict")
			.path(PATH);
	}

}
