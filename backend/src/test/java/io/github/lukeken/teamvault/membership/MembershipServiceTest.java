package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.common.ConflictException;
import io.github.lukeken.teamvault.common.ForbiddenException;
import io.github.lukeken.teamvault.common.NotFoundException;
import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.company.Company;
import io.github.lukeken.teamvault.company.Tier;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit rung: the service is real, two repositories and the guard are mocks
@ExtendWith(MockitoExtension.class)
public class MembershipServiceTest {

	private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final UUID COMPANY = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private static final UUID CAROL = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private MembershipRepository memberships;

	@Mock
	private AppUserRepository users;

	@Mock
	private MembershipGuard guard;

	private MembershipService service;

	private Company acme;

	private AppUser carol;

	@BeforeEach
	void setup() {
		service = new MembershipService(memberships, users, guard);
		acme = new Company("Acme", Tier.SMALL);
		carol = new AppUser("carol@acme.io", "hash", "Carol");
		ReflectionTestUtils.setField(carol, "id", CAROL);
	}

	@Test
	void add_asAdmin_looksUpLowerCasedEmail_checksDuplicateWithFoundUserId_saves() {
		Membership admin = new Membership(new AppUser("alice@acme.io", "hash", "Alice"), acme, Role.ADMIN);
		when(guard.requireAdmin(CALLER, COMPANY)).thenReturn(admin);
		when(users.findByEmail("carol@acme.io")).thenReturn(Optional.of(carol));
		when(memberships.findByUserIdAndCompanyId(any(), any())).thenReturn(Optional.empty());
		when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		MemberResponse response = service.add(CALLER, COMPANY, new AddMemberRequest(" Carol@Acme.io ", Role.MEMBER));

		// Two positions, one captor and one matcher: the duplicate check must use the
		// found user's id, never the caller's.
		ArgumentCaptor<UUID> userId = ArgumentCaptor.forClass(UUID.class);
		verify(memberships).findByUserIdAndCompanyId(userId.capture(), eq(COMPANY));
		assertThat(userId.getValue()).isEqualTo(CAROL);

		ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
		verify(memberships).save(saved.capture());
		assertThat(saved.getValue().getUser()).isSameAs(carol);
		assertThat(saved.getValue().getCompany()).isSameAs(acme);
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);

		assertThat(response.email()).isEqualTo("carol@acme.io");
		assertThat(response.role()).isEqualTo(Role.MEMBER);
	}

	@Test
	void add_alreadyMember_conflict_nothingSaved() {
		when(guard.requireAdmin(CALLER, COMPANY)).thenReturn(new Membership(carol, acme, Role.ADMIN));
		when(users.findByEmail("carol@acme.io")).thenReturn(Optional.of(carol));
		when(memberships.findByUserIdAndCompanyId(CAROL, COMPANY))
			.thenReturn(Optional.of(new Membership(carol, acme, Role.MEMBER)));

		assertThatThrownBy(() -> service.add(CALLER, COMPANY, new AddMemberRequest("carol@acme.io", Role.MEMBER)))
			.isInstanceOfSatisfying(ConflictException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.ALREADY_EXISTS));

		verify(memberships, never()).save(any());
	}

	@Test
	void add_unknownEmail_notFound_nothingSaved() {
		when(guard.requireAdmin(CALLER, COMPANY)).thenReturn(new Membership(carol, acme, Role.ADMIN));
		when(users.findByEmail("dave@acme.io")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.add(CALLER, COMPANY, new AddMemberRequest("dave@acme.io", Role.MEMBER)))
			.isInstanceOfSatisfying(NotFoundException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.NOT_FOUND));

		verify(memberships, never()).save(any());
	}

	// The guard is the gate: when it throws, the service must not have touched a
	// repository.
	@Test
	void add_callerNotAdmin_guardExceptionPropagates_noLookupsAtAll() {
		when(guard.requireAdmin(CALLER, COMPANY)).thenThrow(
				new ForbiddenException(ProblemType.ADMIN_ROLE_REQUIRED, "Requires the ADMIN role in this company"));

		assertThatThrownBy(() -> service.add(CALLER, COMPANY, new AddMemberRequest("carol@acme.io", Role.MEMBER)))
			.isInstanceOfSatisfying(ForbiddenException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.ADMIN_ROLE_REQUIRED));

		verifyNoInteractions(users, memberships);
	}

}
