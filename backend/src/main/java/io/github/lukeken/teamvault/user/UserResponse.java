package io.github.lukeken.teamvault.user;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
