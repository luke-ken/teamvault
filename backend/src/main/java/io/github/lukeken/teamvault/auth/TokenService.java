package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.user.AuthenticatedUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Issues the self-signed access token (ADR-004). Claims are identity only: who the
 * caller is, never what they may do. Authorization stays a per-request database
 * lookup in the service layer (ADR-003), so nothing in the token can go stale except
 * the identity itself, and that window is the TTL.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public TokenResponse issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.ttl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.id().toString())    // UUID, stable across email changes
                .claim("email", user.email())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, expiresAt);
    }
}
