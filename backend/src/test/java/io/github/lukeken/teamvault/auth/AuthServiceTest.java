package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.common.ConflictException;
import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import io.github.lukeken.teamvault.user.AuthenticatedUser;
import io.github.lukeken.teamvault.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit rung: the service is real, its four collaborators are mocks. Queries are stubbed,
// the one command whose content matters (save) is verified through a captor.
@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

	@Mock
	private AppUserRepository users;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private TokenService tokens;

	private AuthService service;

	@BeforeEach
	void setup() {
		service = new AuthService(users, encoder, authenticationManager, tokens);
	}

	@Test
	void register_mixedCaseEmailWithSpaces_lowerCasedForLookupAndSave() {
		when(users.existsByEmail("alice@acme.io")).thenReturn(false);
		when(encoder.encode("hunter22!")).thenReturn("$2a$10$hash");
		when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = service.register(new RegisterRequest("  Alice@Acme.IO ", "hunter22!", " Alice Admin "));

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());

		assertThat(saved.getValue().getEmail()).isEqualTo("alice@acme.io");
		assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice Admin");
		assertThat(response.email()).isEqualTo("alice@acme.io");
	}

	@Test
	void register_password_storedAsEncoderOutputNeverRaw() {
		when(users.existsByEmail("bob@acme.io")).thenReturn(false);
		when(encoder.encode("correct horse")).thenReturn("$2a$10$encoded");
		when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.register(new RegisterRequest("bob@acme.io", "correct horse", "Bob"));

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());

		assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded").doesNotContain("correct horse");
	}

	@Test
	void register_existingEmail_conflict_nothingHashedOrSaved() {
		when(users.existsByEmail("alice@acme.io")).thenReturn(true);

		assertThatThrownBy(() -> service.register(new RegisterRequest("ALICE@acme.io", "hunter22!", "Alice")))
			.isInstanceOfSatisfying(ConflictException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.ALREADY_EXISTS));

		verify(users, never()).save(any());
		verifyNoInteractions(encoder);
	}

	@Test
	void login_validCredentials_tokenIssuedForTheAuthenticatedPrincipal() {
		AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), "alice@acme.io", "$2a$10$hash");
		TokenResponse issued = new TokenResponse("jwt", Instant.parse("2026-10-25T12:00:00Z"));

		when(authenticationManager.authenticate(any()))
			.thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
		when(tokens.issue(principal)).thenReturn(issued);

		TokenResponse response = service.login(new LoginRequest("alice@acme.io", "hunter22!"));

		assertThat(response).isSameAs(issued);

		ArgumentCaptor<Authentication> request = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationManager).authenticate(request.capture());

		assertThat(request.getValue().getName()).isEqualTo("alice@acme.io");
		assertThat(request.getValue().getCredentials()).isEqualTo("hunter22!");
	}

	@Test
	void login_rejectedByManager_exceptionPropagates_noTokenIssued() {
		when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

		assertThatThrownBy(() -> service.login(new LoginRequest("alice@acme.io", "wrong")))
			.isInstanceOf(BadCredentialsException.class);

		verifyNoInteractions(tokens);
	}

}
