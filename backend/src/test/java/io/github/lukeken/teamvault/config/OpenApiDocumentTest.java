package io.github.lukeken.teamvault.config;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The contract is public, the UI is a dev tool (ADR-006). The last test exports the
 * document to docs/openapi.yaml: the committed file is the deliverable, the diff in
 * review is the point.
 */
public class OpenApiDocumentTest extends AbstractIntegrationTest {

	private static final Path EXPORT = Path.of("..", "docs", "openapi.yaml");

	@Test
	void document_isPublic_andDeclaresTheBearerScheme() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.openapi", startsWith("3.1")))
			.andExpect(jsonPath("$.components.securitySchemes.bearer.scheme", is("bearer")))
			.andExpect(jsonPath("$.components.securitySchemes.bearer.bearerFormat", is("JWT")))
			.andExpect(jsonPath("$.security[0].bearer").exists());
	}

	@Test
	void document_coversOnlyTheApi_andKnowsMe() throws Exception {
		String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
		Map<String, Object> paths = JsonPath.read(body, "$.paths");
		assertThat(paths.keySet()).allMatch(p -> p.startsWith("/api/")).contains("/api/me", "/api/auth/login");
	}

	@Test
	void securedOperationsGet401_publicOnesOptOut() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.paths['/api/me'].get.responses['401']").exists())
			.andExpect(jsonPath("$.paths['/api/me'].get.responses['default']").exists())
			.andExpect(jsonPath("$.paths['/api/ping'].get.security", hasSize(0)))
			.andExpect(jsonPath("$.paths['/api/ping'].get.responses['401']").doesNotExist());
	}

	@Test
	void problemDetailSchema_carriesTheExtensionMembers() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.requestId").exists())
			.andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors.type", is("array")))
			.andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.type.format", is("uri")));
	}

	@Test
	void swaggerUi_isOffOutsideDev() throws Exception {
		// Disabled UI: nothing is mapped, so the advice answers a 404 problem, not a 401.
		mvc.perform(get("/swagger-ui.html"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

	@Test
	void export_writesTheCommittedDocument() throws Exception {
		String yaml = mvc.perform(get("/v3/api-docs.yaml"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(yaml).contains("openapi: 3.1").contains("/api/me:").contains("ProblemDetail:");
		Files.writeString(EXPORT, yaml);
	}

}
