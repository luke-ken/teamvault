package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.common.ForbiddenException;
import io.github.lukeken.teamvault.common.ProblemType;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ADR-003 enforcement in one place: the caller's membership decides the scope. The caller
 * id is the verified JWT subject (ADR-004), so no user lookup is needed to know who
 * calls.
 */
@Component
public class MembershipGuard {

	private final MembershipRepository memberships;

	public MembershipGuard(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	/**
	 * 403 for non-members whether or not the company exists: error codes never probe
	 * existence.
	 */
	public Membership requireMember(UUID callerId, UUID companyId) {
		return memberships.findByUserIdAndCompanyId(callerId, companyId)
			.orElseThrow(() -> new ForbiddenException(ProblemType.MEMBERSHIP_REQUIRED, "Not a member of this company"));
	}

	/**
	 * Role on top of membership. A member learns what is missing, a non-member learns
	 * nothing more.
	 */
	public Membership requireAdmin(UUID callerId, UUID companyId) {
		Membership membership = requireMember(callerId, companyId);
		if (membership.getRole() != Role.ADMIN) {
			throw new ForbiddenException(ProblemType.ADMIN_ROLE_REQUIRED, "Requires the ADMIN role in this company");
		}
		return membership;
	}

}
