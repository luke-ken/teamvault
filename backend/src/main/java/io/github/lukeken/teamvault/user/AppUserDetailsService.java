package io.github.lukeken.teamvault.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public AppUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		// Same normalisation as registration, so Alice@Acme.example logs in as
		// alice@acme.example.
		String normalised = email.strip().toLowerCase(Locale.ROOT);
		AppUser user = appUserRepository.findByEmail(normalised)
			.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
		return AuthenticatedUser.from(user);
	}

}
