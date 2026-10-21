package io.github.lukeken.teamvault.company;

import io.github.lukeken.teamvault.common.ForbiddenException;
import io.github.lukeken.teamvault.membership.Membership;
import io.github.lukeken.teamvault.membership.MembershipGuard;
import io.github.lukeken.teamvault.membership.MembershipRepository;
import io.github.lukeken.teamvault.membership.Role;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompanyService {

	private final CompanyRepository companies;

	private final MembershipRepository memberships;

	private final AppUserRepository users;

	private final MembershipGuard guard;

	public CompanyService(CompanyRepository companies, MembershipRepository memberships, AppUserRepository users,
			MembershipGuard guard) {
		this.companies = companies;
		this.memberships = memberships;
		this.users = users;
		this.guard = guard;
	}

	/**
	 * Company and its first ADMIN membership in one transaction: a company without an
	 * admin would be unreachable for everyone, including its creator.
	 */
	@Transactional
	public CompanyResponse create(UUID callerId, CreateCompanyRequest request) {
		// The token proves the caller existed when it was issued (ADR-004). Within the
		// TTL
		// the row can be gone; that is a 403, not a foreign-key error dressed up as 409.
		AppUser caller = users.findById(callerId).orElseThrow(() -> new ForbiddenException("Caller no longer exists"));
		Company company = companies.save(new Company(request.name().strip(), request.tier()));
		memberships.save(new Membership(caller, company, Role.ADMIN));
		return CompanyResponse.from(company, Role.ADMIN);
	}

	@Transactional(readOnly = true)
	public CompanyResponse get(UUID callerId, UUID companyId) {
		Membership membership = guard.requireMember(callerId, companyId);
		return CompanyResponse.from(membership.getCompany(), membership.getRole());
	}

	/**
	 * The companies the caller belongs to, with the role in each: what a UI needs after
	 * login.
	 */
	@Transactional
	public List<CompanyResponse> mine(UUID callerId) {
		return memberships.findByUserId(callerId)
			.stream()
			.map(m -> CompanyResponse.from(m.getCompany(), m.getRole()))
			.toList();
	}

}
