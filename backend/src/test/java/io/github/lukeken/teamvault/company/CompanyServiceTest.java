package io.github.lukeken.teamvault.company;

import io.github.lukeken.teamvault.common.ForbiddenException;
import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.membership.Membership;
import io.github.lukeken.teamvault.membership.MembershipGuard;
import io.github.lukeken.teamvault.membership.MembershipRepository;
import io.github.lukeken.teamvault.membership.Role;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit rung: the service is real, three repositories and the guard are mocks.
@ExtendWith(MockitoExtension.class)
public class CompanyServiceTest {

	private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final UUID COMPANY = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Mock
	private CompanyRepository companies;

	@Mock
	private MembershipRepository memberships;

	@Mock
	private AppUserRepository users;

	@Mock
	private MembershipGuard guard;

	private CompanyService service;

	@BeforeEach
	void setup() {
		service = new CompanyService(companies, memberships, users, guard);
	}

	@Test
	void create_savesCompanyAndAdminMembershipForTheCaller() {
		AppUser caller = new AppUser("alice@acme.io", "hash", "Alice");
		when(users.findById(CALLER)).thenReturn(Optional.of(caller));
		when(companies.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CompanyResponse response = service.create(CALLER, new CreateCompanyRequest("  Acme  ", Tier.SMALL));

		ArgumentCaptor<Company> company = ArgumentCaptor.forClass(Company.class);
		verify(companies).save(company.capture());
		assertThat(company.getValue().getName()).isEqualTo("Acme");
		assertThat(company.getValue().getTier()).isEqualTo(Tier.SMALL);

		ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
		verify(memberships).save(membership.capture());
		assertThat(membership.getValue().getUser()).isSameAs(caller);
		assertThat(membership.getValue().getCompany()).isSameAs(company.getValue());
		assertThat(membership.getValue().getRole()).isEqualTo(Role.ADMIN);

		assertThat(response.name()).isEqualTo("Acme");
		assertThat(response.role()).isEqualTo(Role.ADMIN);
	}

	@Test
	void create_callerRowGone_forbidden_nothingSaved() {
		when(users.findById(CALLER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(CALLER, new CreateCompanyRequest("Acme", Tier.SMALL)))
			.isInstanceOfSatisfying(ForbiddenException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.FORBIDDEN));

		verifyNoInteractions(companies, memberships);
	}

	@Test
	void get_delegatesTheScopeCheckToTheGuard_andReportsTheCallerRole() {
		Company acme = new Company("Acme", Tier.LARGE);
		when(guard.requireMember(CALLER, COMPANY))
			.thenReturn(new Membership(new AppUser("bob@acme.io", "hash", "Bob"), acme, Role.MEMBER));

		CompanyResponse response = service.get(CALLER, COMPANY);

		assertThat(response.name()).isEqualTo("Acme");
		assertThat(response.tier()).isEqualTo(Tier.LARGE);
		assertThat(response.role()).isEqualTo(Role.MEMBER);
	}

	@Test
	void mine_oneEntryPerMembership_withTheRoleInEach() {
		AppUser alice = new AppUser("alice@acme.io", "hash", "Alice");
		when(memberships.findByUserId(CALLER))
			.thenReturn(List.of(new Membership(alice, new Company("Acme", Tier.SMALL), Role.ADMIN),
					new Membership(alice, new Company("Globex", Tier.LARGE), Role.MEMBER)));

		List<CompanyResponse> mine = service.mine(CALLER);

		assertThat(mine).extracting(CompanyResponse::name, CompanyResponse::role)
			.containsExactly(Tuple.tuple("Acme", Role.ADMIN), Tuple.tuple("Globex", Role.MEMBER));
	}

}
