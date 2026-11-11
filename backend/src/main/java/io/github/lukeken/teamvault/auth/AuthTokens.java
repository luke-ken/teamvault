package io.github.lukeken.teamvault.auth;

/**
 * What login and refresh produce (ADR-007): the access token for the JSON body and the
 * raw refresh token for the cookie. The controller splits them; the service never sees a
 * cookie.
 */
public record AuthTokens(TokenResponse access, String refreshToken) {
}
