package io.github.lukeken.teamvault.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class AuthApiIntegrationTest {

    static final String ACME = "11111111-1111-1111-1111-111111111111";
    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    // The production decoder, so a wrong key or issuer fails here and not only live.
    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void register_thenLogin_tokenCarriesUserIdAsSubject() throws Exception {
        String registered = mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(register("Carol@Example.com", PASSWORD, "Carol")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("carol@example.com")))
                .andExpect(jsonPath("$.displayName", is("Carol")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String userId = JsonPath.read(registered, "$.id");

        String loggedIn = mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(login("carol@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(loggedIn, "$.token");

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("email")).isEqualTo("carol@example.com");
    }

    @Test
    void register_sameEmailTwice_409_regardlessOfCase() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(register("dave@example.com", PASSWORD, "Dave")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(register("DAVE@example.com", PASSWORD, "Dave again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email already registered")));
    }

    @Test
    void register_shortPassword_400_withFieldName() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(register("erin@example.com", "short", "Erin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("password ")));
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_sameBody() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(register("frank@example.com", PASSWORD, "Frank")))
                .andExpect(status().isCreated());

        String wrongPassword = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("frank@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // No account enumeration through login: byte-identical answers.
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void garbageToken_401_asJsonWithChallenge() throws Exception {
        mvc.perform(get("/api/companies/" + ACME + "/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.status", is(401)));
    }

    private static String register(String email, String password, String displayName) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"displayName\":\"" + displayName + "\"}";
    }

    private static String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
