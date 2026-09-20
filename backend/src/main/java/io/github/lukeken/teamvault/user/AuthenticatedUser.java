package io.github.lukeken.teamvault.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The principal Spring Security works with during login. Carries the user id so the
 * token issuer can set {@code sub} without a second lookup (ADR-004: sub is the UUID,
 * stable across email changes).
 *
 * No global authorities on purpose: roles are per membership and checked in the
 * service layer (ADR-003), not at the filter-chain level.
 */
public record AuthenticatedUser(UUID id, String email, String passwordHash) implements UserDetails {

    public static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Never print the hash, even by accident in a log line. */
    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", email=" + email + "]";
    }
}
