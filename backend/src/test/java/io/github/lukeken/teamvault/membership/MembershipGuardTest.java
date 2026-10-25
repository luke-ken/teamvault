package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.common.ForbiddenException;
import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.company.Company;
import io.github.lukeken.teamvault.company.Tier;
import io.github.lukeken.teamvault.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MembershipGuardTest {

	private static final UUID CALLER = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final UUID COMPANY = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Mock
	private MembershipRepository memberships;

	private MembershipGuard guard;

	@BeforeEach
	void setup() {
		guard = new MembershipGuard(memberships);
	}

	@Test
	void requireMember_nonMember_membershipRequired() {
		when(memberships.findByUserIdAndCompanyId(CALLER, COMPANY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> guard.requireMember(CALLER, COMPANY)).isInstanceOfSatisfying(ForbiddenException.class,
				e -> assertThat(e.type()).isEqualTo(ProblemType.MEMBERSHIP_REQUIRED));
	}

	@Test
	void requireMember_member_returnsTheMembership() {
		Membership membership = membership(Role.MEMBER);
		when(memberships.findByUserIdAndCompanyId(CALLER, COMPANY)).thenReturn(Optional.of(membership));

		assertThat(guard.requireMember(CALLER, COMPANY)).isSameAs(membership);
	}

	@Test
	void requireAdmin_memberWithoutAdminRole_adminRoleRequired() {
		Membership membership = membership(Role.MEMBER);
		when(memberships.findByUserIdAndCompanyId(CALLER, COMPANY)).thenReturn(Optional.of(membership));

		assertThatThrownBy(() -> guard.requireAdmin(CALLER, COMPANY)).isInstanceOfSatisfying(ForbiddenException.class,
				e -> assertThat(e.type()).isEqualTo(ProblemType.ADMIN_ROLE_REQUIRED));
	}

	// A non-member learns nothing more from the admin check than from the member check:
	// the error must not reveal that the company exists or what role is missing
	@Test
	void requireAdmin_nonMember_membershipRequired_notAdminRoleRequired() {
		when(memberships.findByUserIdAndCompanyId(CALLER, COMPANY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> guard.requireAdmin(CALLER, COMPANY)).isInstanceOfSatisfying(ForbiddenException.class,
				e -> assertThat(e.type()).isEqualTo(ProblemType.MEMBERSHIP_REQUIRED));
	}

	@Test
	void requireAdmin_admin_returnsTheMembership() {
		Membership membership = membership(Role.ADMIN);
		when(memberships.findByUserIdAndCompanyId(CALLER, COMPANY)).thenReturn(Optional.of(membership));

		assertThat(guard.requireAdmin(CALLER, COMPANY)).isSameAs(membership);
	}

	private static Membership membership(Role role) {
		AppUser user = new AppUser("bob@acme.io", "hash", "Bob");
		Company company = new Company("Acme", Tier.SMALL);
		return new Membership(user, company, role);
	}

}
