package io.github.lukeken.teamvault.company;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CompanyApiIntegrationTest extends AbstractIntegrationTest {

	@Test
	void create_makesCallerAdmin_visibleOnGetAndUnderMine() throws Exception {
		String body = mvc
			.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Initech\",\"tier\":\"LARGE\"}")
				.with(bearer(BOB)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name", is("Initech")))
			.andExpect(jsonPath("$.tier", is("LARGE")))
			.andExpect(jsonPath("$.role", is("ADMIN")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String companyId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/companies/" + companyId).with(bearer(BOB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role", is("ADMIN")));

		// Bob now sits in Globex as MEMBER and in Initech as ADMIN; the filter picks
		// Initech.
		mvc.perform(get("/api/companies").with(bearer(BOB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + companyId + "')].role", contains("ADMIN")))
			.andExpect(jsonPath("$[?(@.id == '" + GLOBEX + "')].role", contains("MEMBER")));
	}

	// Same rule as files (ADR-003): a non-member gets 403, and an unknown id looks
	// exactly
	// the same, so nobody can enumerate companies through error codes.
	@Test
	void get_asNonMember_403_evenForUnknownId() throws Exception {
		mvc.perform(get("/api/companies/" + ACME).with(bearer(BOB))).andExpect(status().isForbidden());
		mvc.perform(get("/api/companies/" + UUID.randomUUID()).with(bearer(BOB))).andExpect(status().isForbidden());
	}

	@Test
	void create_blankName_400_namingTheField() throws Exception {
		mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"  \",\"tier\":\"SMALL\"}")
			.with(bearer(ALICE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", is("Validation failed")))
			.andExpect(jsonPath("$.errors[0].field", is("name")));
	}

	@Test
	void create_unknownTier_400_notA500() throws Exception {
		mvc.perform(post("/api/companies").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Hooli\",\"tier\":\"HUGE\"}")
			.with(bearer(ALICE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail", is("Malformed request body")));
	}

}
