package io.github.lukeken.teamvault.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The login-time half of authentication (ADR-004): checks email and password against
 * app_user through the same UserDetailsService and bcrypt encoder HTTP Basic uses.
 * Kept apart from SecurityConfig so the ping slice test can import the filter chain
 * without a database-backed UserDetailsService.
 *
 * DaoAuthenticationProvider hides "user not found" behind BadCredentialsException and
 * runs a dummy bcrypt check for unknown emails, so response and timing look the same
 * for a wrong password and a wrong email.
 */
@Configuration
public class AuthenticationConfig {

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
