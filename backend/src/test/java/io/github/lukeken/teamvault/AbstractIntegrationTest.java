package io.github.lukeken.teamvault;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One configuration for every API test, so Spring's context cache boots the application
 * and the Postgres container once per test run instead of once per test class.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class AbstractIntegrationTest {

	// Fixed ids from V2__seed.sql: alice is a member of Acme only, bob of Globex only.
	protected static final String ACME = "11111111-1111-1111-1111-111111111111";

	protected static final String GLOBEX = "22222222-2222-2222-2222-222222222222";

	protected static final String ALICE = "alice@acme.example";

	protected static final String BOB = "bob@globex.example";

	protected static final String PASSWORD = "devpass12";

	// Not @TempDir: JUnit removes a class's temp dir when that class is done, while the
	// cached context would keep writing blobs into it from the next class on.
	private static final Path STORAGE_DIR = createStorageDir();

	@DynamicPropertySource
	static void storageProps(DynamicPropertyRegistry registry) {
		registry.add("teamvault.storage.dir", STORAGE_DIR::toString);
	}

	@Autowired
	protected MockMvc mvc;

	// One real login per user per test run (ADR-004): the token goes through the same
	// decoder as production traffic, so a broken key or issuer fails these tests too.
	// Static on purpose: JUnit creates a new instance per test method, the token is
	// valid for 15 minutes, and the test profile pins the secret.
	private static final Map<String, String> TOKENS = new HashMap<>();

	protected RequestPostProcessor bearer(String email) throws Exception {
		String token = TOKENS.get(email);
		if (token == null) {
			token = login(email, PASSWORD);
			TOKENS.put(email, token);
		}
		String header = "Bearer " + token;
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, header);
			return request;
		};
	}

	protected String login(String email, String password) throws Exception {
		String body = mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private static Path createStorageDir() {
		try {
			return Files.createTempDirectory("teamvault-test-blobs");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
