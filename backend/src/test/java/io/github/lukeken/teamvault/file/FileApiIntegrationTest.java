package io.github.lukeken.teamvault.file;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileApiIntegrationTest extends AbstractIntegrationTest {

	@Test
	void uploadListDownload_roundTrip_withinOwnCompany() throws Exception {
		var upload = new MockMultipartFile("file", "report.txt", "text/plain", "quarterly numbers".getBytes());

		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.filename", is("report.txt")))
			.andExpect(jsonPath("$.uploadedBy", is("Alice Admin")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fileId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/companies/" + ACME + "/files").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id", is(fileId)));

		mvc.perform(get("/api/companies/" + ACME + "/files/" + fileId + "/download").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(content().contentType("text/plain"))
			.andExpect(content().string("quarterly numbers"));
	}

	// THE negative test (ADR-003): cross-tenant access must fail at the backend.
	@Test
	void crossTenantAccess_isImpossible() throws Exception {
		var upload = new MockMultipartFile("file", "secret.txt", "text/plain", "acme only".getBytes());
		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String acmeFileId = JsonPath.read(body, "$.id");

		// Bob is no member of Acme: listing and direct reads are forbidden.
		mvc.perform(get("/api/companies/" + ACME + "/files").with(bearer(BOB))).andExpect(status().isForbidden());
		mvc.perform(get("/api/companies/" + ACME + "/files/" + acmeFileId + "/download").with(bearer(BOB)))
			.andExpect(status().isForbidden());

		// The sneaky path: Bob queries an Acme file id under HIS company. The
		// company-scoped lookup makes it a 404, not a leak.
		mvc.perform(get("/api/companies/" + GLOBEX + "/files/" + acmeFileId + "/download").with(bearer(BOB)))
			.andExpect(status().isNotFound());
	}

	@Test
	void duplicateFilenameInSameCompany_conflicts() throws Exception {
		var upload = new MockMultipartFile("file", "twice.txt", "text/plain", "v1".getBytes());
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated());
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isConflict());
	}

	@Test
	void withoutCredentials_401() throws Exception {
		mvc.perform(get("/api/companies/" + ACME + "/files")).andExpect(status().isUnauthorized());
	}

}
