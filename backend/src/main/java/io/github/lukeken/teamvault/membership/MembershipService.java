package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.common.ConflictException;
import io.github.lukeken.teamvault.common.NotFoundException;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MembershipService {

	private final MembershipRepository memberships;

	private final AppUserRepository users;

	private final MembershipGuard guard;

	public MembershipService(MembershipRepository memberships, AppUserRepository users, MembershipGuard guard) {
		this.memberships = memberships;
		this.users = users;
		this.guard = guard;
	}

	/**
	 * The first real role check: membership alone is not enough, the caller must be
	 * ADMIN. A 404 for an unknown email does tell an admin which addresses have an
	 * account; the alternative, an invitation by email, needs a mail channel and is on
	 * the ladder.
	 */
	@Transactional
	public MemberResponse add(UUID callerId, UUID companyId, AddMemberRequest request) {
		Membership admin = guard.requireAdmin(callerId, companyId);
		String email = request.email().strip().toLowerCase(Locale.ROOT);
		AppUser user = users.findByEmail(email).orElseThrow(() -> new NotFoundException("No user with that email"));

		if (memberships.findByUserIdAndCompanyId(user.getId(), companyId).isPresent()) {
			throw new ConflictException("Already a member of this company");
		}

		// uq_membership_company_user catches the race between the check and the insert;
		// the
		// advice maps that to 409 as well.
		Membership saved = memberships.save(new Membership(user, admin.getCompany(), request.role()));
		return MemberResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> list(UUID callerId, UUID companyId) {
		guard.requireMember(callerId, companyId);
		return memberships.findByCompanyId(companyId).stream().map(MemberResponse::from).toList();
	}

}
