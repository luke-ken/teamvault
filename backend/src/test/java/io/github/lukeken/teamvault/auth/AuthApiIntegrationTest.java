package io.github.lukeken.teamvault.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthApiIntegrationTest extends AbstractIntegrationTest {

	static final String NEW_PASSWORD = "correct-horse-battery";

	// The production decoder, so a wrong key or issuer fails here and not only live.
	@Autowired
	JwtDecoder jwtDecoder;

	@Test
	void register_thenLogin_tokenCarriesUserIdAsSubject() throws Exception {
		String registered = mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("Carol@Example.com", NEW_PASSWORD, "Carol")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email", is("carol@example.com")))
			.andExpect(jsonPath("$.displayName", is("Carol")))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String userId = JsonPath.read(registered, "$.id");

		String loggedIn = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("carol@example.com", NEW_PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String token = JsonPath.read(loggedIn, "$.token");

		Jwt jwt = jwtDecoder.decode(token);
		assertThat(jwt.getSubject()).isEqualTo(userId);
		assertThat(jwt.getClaimAsString("email")).isEqualTo("carol@example.com");
	}

	@Test
	void register_sameEmailTwice_409_regardlessOfCase() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(registerBody("dave@example.com", NEW_PASSWORD, "Dave"))).andExpect(status().isCreated());
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(registerBody("DAVE@example.com", NEW_PASSWORD, "Dave again")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", is("Email already registered")));
	}

	@Test
	void register_shortPassword_400_withFieldName() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(registerBody("erin@example.com", "short", "Erin")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", is("Validation failed")))
			.andExpect(jsonPath("$.errors[0].field", is("password")));
	}

	@Test
	void login_wrongPasswordAndUnknownEmail_sameBody() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(registerBody("frank@example.com", NEW_PASSWORD, "Frank"))).andExpect(status().isCreated());

		String wrongPassword = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("frank@example.com", "not-the-password")))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownEmail = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("nobody@example.com", NEW_PASSWORD)))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		// No account enumeration through login: identical answers once the per-request id
		// is out.
		assertThat(withoutRequestId(wrongPassword)).isEqualTo(withoutRequestId(unknownEmail));
	}

	@Test
	void garbageToken_401_asJsonWithChallenge() throws Exception {
		mvc.perform(get("/api/companies/" + ACME + "/files").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
			.andExpect(jsonPath("$.status", is(401)));
	}

	@Test
	void health_isPublic_andReportsOnlyStatus() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("UP")))
			// show-details: never, so no component list reaches the outside.
			.andExpect(jsonPath("$.components").doesNotExist());
		mvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("UP")));
	}

	@Test
	void me_returnsTheCallerFromTheToken() throws Exception {
		mvc.perform(get("/api/me").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
			.andExpect(jsonPath("$.email", is(ALICE)))
			.andExpect(jsonPath("$.displayName", is("Alice Admin")))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	private static String registerBody(String email, String password, String displayName) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"" + displayName
				+ "\"}";
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String withoutRequestId(String body) {
		return body.replaceAll(",?\"requestId\":\"[^\"]*\"", "");
	}

}
