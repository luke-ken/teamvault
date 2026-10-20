package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.common.ConflictException;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import io.github.lukeken.teamvault.user.AuthenticatedUser;
import io.github.lukeken.teamvault.user.UserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, TokenService tokenService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /**
     * The exists check gives a readable 409 in the normal case. Two concurrent
     * registrations of the same email are caught by {@code uq_app_user_email}, which the
     * advice maps to 409 as well. Email is stored lower-cased so the unique constraint
     * is effectively case-insensitive.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        AppUser user = appUserRepository.save(
                new AppUser(email, passwordEncoder.encode(request.password()), request.displayName().strip()));
        return UserResponse.from(user);
    }

    /**
     * Exchanges email and password for a short-lived access token. A failed check throws
     * AuthenticationException; the advice turns it into one 401 message for unknown
     * email and wrong password alike.
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        return tokenService.issue((AuthenticatedUser) authentication.getPrincipal());
    }
}
