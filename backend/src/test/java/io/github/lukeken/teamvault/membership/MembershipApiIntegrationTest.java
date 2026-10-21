package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MembershipApiIntegrationTest extends AbstractIntegrationTest {

	// Own user, registered here: the seed memberships stay untouched because the file
	// test relies on bob being outside Acme, and the database is shared across classes.
	static final String GINA = "gina@acme.example";

	static final String GINA_PASSWORD = "gina-pass-123";

	@Test
	void adminAddsMember_memberCannotAdd_nonMemberCannotSee() throws Exception {
		register(GINA, GINA_PASSWORD, "Gina");

		// alice is ADMIN of Acme (seed): add gina as MEMBER by default.
		mvc.perform(post("/api/companies/" + ACME + "/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"Gina@Acme.example\"}")
			.with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email", is(GINA)))
			.andExpect(jsonPath("$.role", is("MEMBER")));

		mvc.perform(get("/api/companies/" + ACME + "/members").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.email == '" + GINA + "')].role", contains("MEMBER")))
			.andExpect(jsonPath("$[?(@.email == '" + ALICE + "')].role", contains("ADMIN")));

		// The role check: gina is a member now, but only ADMIN may add.
		String ginaToken = login(GINA, GINA_PASSWORD);
		mvc.perform(post("/api/companies/" + ACME + "/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + BOB + "\"}")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + ginaToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", is("Requires the ADMIN role in this company")));

		// A member may list, a non-member may not even see the list.
		mvc.perform(get("/api/companies/" + ACME + "/members").header(HttpHeaders.AUTHORIZATION, "Bearer " + ginaToken))
			.andExpect(status().isOk());
		mvc.perform(get("/api/companies/" + ACME + "/members").with(bearer(BOB))).andExpect(status().isForbidden());
	}

	@Test
	void addTwice_409_andUnknownEmail_404() throws Exception {
		register("hank@acme.example", GINA_PASSWORD, "Hank");
		String body = "{\"email\":\"hank@acme.example\",\"role\":\"ADMIN\"}";

		mvc.perform(post("/api/companies/" + ACME + "/members").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(bearer(ALICE))).andExpect(status().isCreated()).andExpect(jsonPath("$.role", is("ADMIN")));
		mvc.perform(post("/api/companies/" + ACME + "/members").contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(bearer(ALICE))).andExpect(status().isConflict());

		mvc.perform(post("/api/companies/" + ACME + "/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"nobody@acme.example\"}")
			.with(bearer(ALICE))).andExpect(status().isNotFound());
	}

	@Test
	void nonMemberAdmin_cannotAddToForeignCompany() throws Exception {
		// alice is ADMIN of Acme, not a member of Globex: 403 "not a member", never
		// "needs admin".
		mvc.perform(post("/api/companies/" + GLOBEX + "/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + ALICE + "\"}")
			.with(bearer(ALICE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", is("Not a member of this company")));
	}

	private void register(String email, String password, String displayName) throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"" + displayName
					+ "\"}"))
			.andExpect(status().isCreated());
	}

}
