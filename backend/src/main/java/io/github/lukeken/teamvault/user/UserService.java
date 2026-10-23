package io.github.lukeken.teamvault.user;

import io.github.lukeken.teamvault.common.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

	private final AppUserRepository users;

	public UserService(AppUserRepository users) {
		this.users = users;
	}

	// A valid token can outlive its user for one TTL (ADR-004). Same 403 as the company
	// service: a deleted account reads nothing, not even itself.
	public UserResponse me(UUID callerId) {
		return users.findById(callerId)
			.map(UserResponse::from)
			.orElseThrow(() -> new ForbiddenException("Caller no longer exists"));
	}

}
