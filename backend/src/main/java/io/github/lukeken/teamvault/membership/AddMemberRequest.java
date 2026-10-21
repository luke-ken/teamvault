package io.github.lukeken.teamvault.membership;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(@NotBlank @Email String email, Role role) {

	public AddMemberRequest {
		if (role == null) {
			role = Role.MEMBER;
		}
	}
}
