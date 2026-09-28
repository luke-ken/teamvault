package io.github.lukeken.teamvault.ping;

import io.github.lukeken.teamvault.auth.JsonAuthenticationEntryPoint;
import io.github.lukeken.teamvault.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test: the filter chain is real, the decoder is a mock. Ping is permitAll, so
// no token is ever decoded; the mock only satisfies the resource server's wiring.
@WebMvcTest(PingController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class})
class PingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void pingIsPubliclyReachableAndAnswersPong() throws Exception {
		mockMvc.perform(get("/api/ping"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("pong"));
	}

}
