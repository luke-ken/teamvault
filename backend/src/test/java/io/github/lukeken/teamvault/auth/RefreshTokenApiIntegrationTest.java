package io.github.lukeken.teamvault.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The browser, by hand: MockMvc has no cookie jar, so each test carries the Set-Cookie of
 * one response into the next request. Every test registers its own user so its family
 * never touches the cached bearer logins of the other classes (ADR-007).
 */
@ExtendWith(OutputCaptureExtension.class)
public class RefreshTokenApiIntegrationTest extends AbstractIntegrationTest {

	static final String NEW_PASSWORD = "correct-horse-battery";

	static final String INVALID_REFRESH_TOKEN = "#invalid-refresh-token";

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	AppUserRepository users;

	@Test
	void login_setsRefreshCookie_httpOnlySecureStrictScopedToAuth() throws Exception {
		MockCookie cookie = loginCookie("gina@example.com");

		// 32 random bytes, base64url without padding.
		assertThat(cookie.getValue()).hasSize(43);
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Strict");
		assertThat(cookie.getPath()).isEqualTo("/api/auth");
		// 14 days, the idle expiry of the row it names.
		assertThat(cookie.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
	}

	@Test
	void refresh_rotates_newAccessTokenForTheSameUser_newCookieEveryTime() throws Exception {
		MockCookie first = loginCookie("hank@example.com");
		String userId = users.findByEmail("hank@example.com").orElseThrow().getId().toString();

		MvcResult refreshed = mvc.perform(refresh(first))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isString())
			.andReturn();
		MockCookie second = cookieOf(refreshed);
		assertThat(second.getValue()).hasSize(43).isNotEqualTo(first.getValue());
		assertThat(second.getPath()).isEqualTo("/api/auth");

		String token = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");
		assertThat(jwtDecoder.decode(token).getSubject()).isEqualTo(userId);

		// The chain continues from the newest cookie.
		MockCookie third = cookieOf(mvc.perform(refresh(second)).andExpect(status().isOk()).andReturn());
		assertThat(third.getValue()).isNotEqualTo(second.getValue());
	}

	@Test
	void refresh_reuse_revokesFamily_theNewCookieIs401Too(CapturedOutput output) throws Exception {
		MockCookie first = loginCookie("ivan@example.com");
		MockCookie second = cookieOf(mvc.perform(refresh(first)).andExpect(status().isOk()).andReturn());

		// A copy of the used token arrives: proof that two parties hold tokens of the
		// family.
		expectInvalidRefreshToken(mvc.perform(refresh(first)));
		assertThat(output).contains("WARN", "Refresh token reuse: family");

		// The real client's next refresh dies with the family; the user logs in again.
		expectInvalidRefreshToken(mvc.perform(refresh(second)));
	}

	@Test
	void refresh_withoutCookie_401_invalidRefreshToken() throws Exception {
		expectInvalidRefreshToken(mvc.perform(post("/api/auth/refresh")));
	}

	@Test
	void refresh_expiredRow_401() throws Exception {
		// Planted straight into the table: the raw token is known here, the row is past
		// its expiry, and nothing else about it is wrong.
		String raw = "expired-raw-token-" + UUID.randomUUID();
		AppUser alice = users.findByEmail(ALICE).orElseThrow();
		Instant past = Instant.now().minusSeconds(60);
		refreshTokens.save(new RefreshToken(alice, UUID.randomUUID(), RefreshTokenService.hash(raw), past, past));

		expectInvalidRefreshToken(mvc.perform(refresh(new Cookie(RefreshCookie.NAME, raw))));
	}

	@Test
	void logout_204_clearsCookie_refreshAfterwards401() throws Exception {
		MockCookie cookie = loginCookie("judy@example.com");

		MockCookie cleared = cookieOf(mvc.perform(logout(cookie)).andExpect(status().isNoContent()).andReturn());

		// Same name, path and flags as the one being replaced, otherwise the browser
		// keeps both.
		assertThat(cleared.getValue()).isEmpty();
		assertThat(cleared.getMaxAge()).isZero();
		assertThat(cleared.getPath()).isEqualTo("/api/auth");
		assertThat(cleared.isHttpOnly()).isTrue();
		assertThat(cleared.getSecure()).isTrue();
		assertThat(cleared.getSameSite()).isEqualTo("Strict");

		expectInvalidRefreshToken(mvc.perform(refresh(cookie)));

		// Idempotent: a browser that already lost the cookie can still log out.
		mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
	}

	private MockCookie loginCookie(String email) throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\",\"displayName\":\"Test\"}"))
			.andExpect(status().isCreated());

		MvcResult login = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return cookieOf(login);
	}

	private static MockHttpServletRequestBuilder refresh(Cookie cookie) {
		return post("/api/auth/refresh").cookie(cookie);
	}

	private static MockHttpServletRequestBuilder logout(Cookie cookie) {
		return post("/api/auth/logout").cookie(cookie);
	}

	private static MockCookie cookieOf(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
		assertThat(cookie).as("Set-Cookie refresh_token").isNotNull();
		return (MockCookie) cookie;
	}

	/**
	 * One 401 for every bad refresh token, and no bearer challenge: the credential is a
	 * cookie.
	 */
	private static void expectInvalidRefreshToken(ResultActions actions) throws Exception {
		actions.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type", endsWith(INVALID_REFRESH_TOKEN)))
			.andExpect(jsonPath("$.detail", is("Invalid or expired refresh token")))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

}
