package io.github.lukeken.teamvault.config;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Security's default response headers, pinned so a later header customisation
 * cannot drop them unnoticed (OWASP A02). Nothing in SecurityConfig sets them: the test
 * documents what the defaults are and that they reach JSON and download alike.
 */
public class SecurityHeadersApiIntegrationTest extends AbstractIntegrationTest {

	@Test
	void securedJsonResponse_carriesDefaultSecurityHeaders() throws Exception {
		mvc.perform(get("/api/me").with(bearer(ALICE))).andExpect(status().isOk()).andExpectAll(securityHeaders());
	}

	@Test
	void download_carriesSecurityHeaders_andEncodedDisposition() throws Exception {
		var upload = new MockMultipartFile("file", "übersicht 2026.txt", "text/plain", "umlaut".getBytes());
		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fileId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/companies/" + ACME + "/files/" + fileId + "/download").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpectAll(securityHeaders())
			// RFC 6266: a plain filename for old clients, the real name RFC 5987 encoded
			// in filename*. Browsers save the file instead of rendering it, which with
			// nosniff is the defence against an uploaded HTML or SVG page running in our
			// origin. Only the encoded form is pinned: the plain fallback changed shape
			// between spring-web 7.0.8 and 7.0.9.
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					containsString("filename*=UTF-8''%C3%BCbersicht%202026.txt")));
	}

	private static ResultMatcher[] securityHeaders() {
		return new ResultMatcher[] {
				// The browser must not guess a content type: text/plain stays text/plain.
				header().string("X-Content-Type-Options", "nosniff"),
				// No framing by other origins (clickjacking).
				header().string("X-Frame-Options", "DENY"),
				// Private responses, never cached by a shared proxy or the browser.
				header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"),
				header().string(HttpHeaders.PRAGMA, "no-cache"), header().string(HttpHeaders.EXPIRES, "0") };
	}

}
