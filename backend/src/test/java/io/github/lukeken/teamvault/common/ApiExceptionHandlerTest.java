package io.github.lukeken.teamvault.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test around the advice alone: a throwaway controller throws on demand, no database,
// no servlet filters (so no security chain and no request id), and each case asks the
// handler one question. The request id and both exits are covered by the integration test.
@WebMvcTest(ApiExceptionHandlerTest.BoomController.class)
@Import(ApiExceptionHandlerTest.BoomController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void unexpectedException_500_aboutBlank_noInternals() throws Exception {
		mvc.perform(get("/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").doesNotExist())
			.andExpect(jsonPath("$.title", is("Internal Server Error")))
			.andExpect(jsonPath("$.detail", is("Unexpected error")))
			// The exception message stays in the server log.
			.andExpect(content().string(not(containsString("hunter2"))));
	}

	@Test
	void apiException_mapsToItsProblemType() throws Exception {
		mvc.perform(get("/boom/missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.type", endsWith("#not-found")))
			.andExpect(jsonPath("$.detail", is("Widget not found")))
			.andExpect(jsonPath("$.instance", is("/boom/missing")));
	}

	@Test
	void validationFailure_400_errorsListSortedByField() throws Exception {
		mvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type", endsWith("#validation-failed")))
			.andExpect(jsonPath("$.detail", is("Validation failed")))
			.andExpect(jsonPath("$.errors", hasSize(2)))
			.andExpect(jsonPath("$.errors[0].field", is("age")))
			.andExpect(jsonPath("$.errors[0].message", is("must not be null")))
			.andExpect(jsonPath("$.errors[1].field", is("name")))
			.andExpect(jsonPath("$.errors[1].message", is("must not be blank")));
	}

	record Form(@NotBlank String name, @NotNull Integer age) {
	}

	@RestController
	static class BoomController {

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("db password is hunter2");
		}

		@GetMapping("/boom/missing")
		void missing() {
			throw new NotFoundException("Widget not found");
		}

		@PostMapping("/boom/validate")
		void validate(@Valid @RequestBody Form form) {
		}

	}

}
