package io.github.lukeken.teamvault.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Issue and verify side of ADR-004, kept apart from {@code SecurityConfig} so a slice
 * test can import the filter chain without needing key material.
 *
 * Both beans share one HS256 key: TeamVault is the only issuer and the only verifier.
 * The Keycloak upgrade path replaces the decoder bean with an issuer-URI one; nothing
 * that consumes {@link JwtDecoder} changes.
 */
@Configuration
public class JwtConfig {

    /** Value of the {@code iss} claim; the decoder rejects tokens that carry any other. */
    public static final String ISSUER = "teamvault";

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validator = exp/nbf with 60 s clock skew; plus our issuer check.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private static SecretKey secretKey(JwtProperties props) {
        return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
