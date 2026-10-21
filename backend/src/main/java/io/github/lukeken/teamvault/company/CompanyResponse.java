package io.github.lukeken.teamvault.company;

import io.github.lukeken.teamvault.membership.Role;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(UUID id, String name, Tier tier, Role role, Instant createdAt) {

	static CompanyResponse from(Company company, Role callerRole) {
		return new CompanyResponse(company.getId(), company.getName(), company.getTier(), callerRole,
				company.getCreatedAt());
	}
}
