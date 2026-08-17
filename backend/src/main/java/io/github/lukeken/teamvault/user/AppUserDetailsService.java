package io.github.lukeken.teamvault.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public AppUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		AppUser user = appUserRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
		// No global authorities: roles are per-company (membership.role) and are
		// checked in the service layer, not at the Spring Security level.
		return User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.authorities(List.of())
				.build();
	}
}
