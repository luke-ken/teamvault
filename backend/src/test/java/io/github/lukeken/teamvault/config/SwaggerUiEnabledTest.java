package io.github.lukeken.teamvault.config;

import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The one switch the dev profile flips, proven in the "on" position. Costs a second Spring
// context and Postgres container; the only test class that does.
@TestPropertySource(properties = "springdoc.swagger-ui.enabled=true")
public class SwaggerUiEnabledTest extends AbstractIntegrationTest {

	@Test
	void swaggerUi_answersWhenEnabled() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
