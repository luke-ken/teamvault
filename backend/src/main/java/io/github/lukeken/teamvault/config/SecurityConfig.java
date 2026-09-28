package io.github.lukeken.teamvault.config;

import io.github.lukeken.teamvault.auth.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint) throws Exception {
		http
				// Pure token API, no cookie or session auth: CSRF does not apply.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/ping").permitAll()
						// Login is where you request a token, so it cannot demand one.
						.requestMatchers("/api/auth/**").permitAll()
						.anyRequest().authenticated())
				// ADR-004: bearer tokens verified by the JwtDecoder bean (JwtConfig).
				// The entry point here answers "token present but rejected".
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(entryPoint))
				// Same entry point for "no token at all", so both 401 paths return
				// the ApiError JSON shape instead of Spring's empty default body.
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
