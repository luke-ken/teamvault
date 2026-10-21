package io.github.lukeken.teamvault.membership;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, String displayName, Role role, Instant since) {

	static MemberResponse from(Membership membership) {
		return new MemberResponse(membership.getUser().getId(), membership.getUser().getEmail(),
				membership.getUser().getDisplayName(), membership.getRole(), membership.getCreatedAt());
	}
}
