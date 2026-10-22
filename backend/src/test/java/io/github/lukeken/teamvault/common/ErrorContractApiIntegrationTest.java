package io.github.lukeken.teamvault.common;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The error contract (ADR-005) as a client sees it: one body shape whether the failure
 * comes from the security chain or from a controller, and the request id on every
 * response.
 */
public class ErrorContractApiIntegrationTest extends AbstractIntegrationTest {

	private final static String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private final static String ACME_FILES = "/api/companies/" + ACME + "/files";

	// --- security chain exit ---

	@Test
	void noToken_401_authenticationRequired_withChallenge() throws Exception {
		MvcResult result = mvc.perform(get(ACME_FILES))
			.andExpectAll(problem(HttpStatus.UNAUTHORIZED, "authentication-required", "Authentication required"))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.instance", is(ACME_FILES)))
			.andReturn();

		// The id in the body is the id in the header: one string to grep in the log.
		String inBody = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
		assertThat(inBody).isEqualTo(result.getResponse().getHeader(RequestIdFilter.HEADER));
	}

	@Test
	void garbageToken_401_invalidToken_withChallenge() throws Exception {
		mvc.perform(get(ACME_FILES).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
			.andExpectAll(problem(HttpStatus.UNAUTHORIZED, "invalid-token", "Invalid or expired token"))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
	}

	@Test
	void wrongPassword_401_badCredentials_noChallenge() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + ALICE + "\",\"password\":\"wrong-pass\"}"))
			.andExpectAll(problem(HttpStatus.UNAUTHORIZED, "bad-credentials", "Invalid email or password"))
			// The RFC 6750 challenge belongs to bearer-protected resources; login is not
			// one.
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void nonMember_403_membershipRequired() throws Exception {
		mvc.perform(get("/api/companies/" + GLOBEX + "/files").with(bearer(ALICE)))
			.andExpectAll(problem(HttpStatus.FORBIDDEN, "membership-required", "Not a member of this company"));
	}

	@Test
	void unreadableBody_400_malformedBody() throws Exception {
		mvc.perform(
				post("/api/companies").contentType(MediaType.APPLICATION_JSON).content("{not json").with(bearer(ALICE)))
			.andExpectAll(problem(HttpStatus.BAD_REQUEST, "malformed-body", "Malformed request body"));
	}

	// Spring's own exception, no catalogue entry: about:blank, yet the same shape and the
	// request id, because the base class funnels through handleExceptionInternal.
	@Test
	void unknownRoute_404_aboutBlank_sameShape() throws Exception {
		mvc.perform(get("/api/nope").with(bearer(ALICE)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			// RFC 9457: no type member means about:blank. Spring 7 leaves it out.
			.andExpect(jsonPath("$.type").doesNotExist())
			.andExpect(jsonPath("$.title", is("Not Found")))
			.andExpect(jsonPath("$.status", is(404)))
			.andExpect(jsonPath("$.requestId", matchesPattern(UUID_PATTERN)));
	}

	@Test
	void success_carriesGeneratedRequestIdHeader() throws Exception {
		mvc.perform(get("/api/ping"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
	}

	@Test
	void validClientRequestId_isEchoedInHeaderAndBody() throws Exception {
		mvc.perform(get(ACME_FILES).header(RequestIdFilter.HEADER, "client-trace.0042"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(RequestIdFilter.HEADER, "client-trace.0042"))
			.andExpect(jsonPath("$.requestId", is("client-trace.0042")));
	}

	@Test
	void invalidClientRequestId_isReplaced() throws Exception {
		// Too short, and a space is outside the safe alphabet: a client must not be able
		// to write arbitrary text into our log line.
		mvc.perform(get("/api/ping").header(RequestIdFilter.HEADER, "bad id!"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
	}

	private static ResultMatcher[] problem(HttpStatus expected, String slug, String detail) {
		return new ResultMatcher[] { status().is(expected.value()),
				content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
				jsonPath("$.type", endsWith("/docs/errors.md#" + slug)),
				jsonPath("$.title", is(expected.getReasonPhrase())), jsonPath("$.status", is(expected.value())),
				jsonPath("$.detail", is(detail)), jsonPath("$.requestId", matchesPattern(UUID_PATTERN)) };
	}

}
