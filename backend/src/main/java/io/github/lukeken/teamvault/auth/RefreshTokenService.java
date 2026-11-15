package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.common.UnauthorizedException;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import io.github.lukeken.teamvault.user.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The stateful half of authentication (ADR-007): issues, rotates and revokes the opaque
 * refresh tokens in {@code refresh_token}. The raw token is 32 random bytes as base64url
 * and leaves this class exactly once, on the way to the cookie; the table holds its
 * SHA-256.
 *
 * Unsalted on purpose: the token is 256 random bits, not a password, so a dictionary has
 * nothing to work with and a slow salted hash would protect nothing. The lookup by hash
 * is what makes the table worthless without the raw tokens.
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	static final int TOKEN_BYTES = 32;

	private static final String DETAIL = "Invalid or expired refresh token";

	private final RefreshTokenRepository refreshTokens;

	private final AppUserRepository users;

	private final JwtProperties props;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokens, AppUserRepository users, JwtProperties props,
			Clock clock) {
		this.refreshTokens = refreshTokens;
		this.users = users;
		this.props = props;
		this.clock = clock;
	}

	/**
	 * What a successful rotation hands back: who the token belonged to, and its
	 * successor.
	 */
	public record Rotation(AuthenticatedUser user, String refreshToken) {
	}

	/**
	 * Starts a new family for a fresh login. The family cap is fixed here and copied to
	 * every successor, so a row's expiry already knows about it.
	 */
	@Transactional
	public String issue(UUID userId) {
		Instant now = clock.instant();
		// No select: a reference carries the id, which is all the new row needs.
		AppUser user = users.getReferenceById(userId);
		return insert(user, UUID.randomUUID(), now.plus(props.refreshFamilyTtl()), now);
	}

	/**
	 * Trades a live token for its successor. Three outcomes of the one lookup: live means
	 * rotate; used means a second party holds a token of this family, so the family dies;
	 * anything else (revoked, expired, unknown, no cookie) is a plain 401. One detail
	 * text for every failure, so the response does not say which it was. The reuse path
	 * writes the revocation and then throws, so that exception must not roll it back.
	 */
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public Rotation rotate(String rawToken) {
		if (rawToken == null) {
			throw invalid();
		}

		Instant now = clock.instant();
		RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
		if (current.isUsed()) {
			revokeFamily(current.getFamilyId(), now);
			// OWASP A09: the one signal rotation exists for. Ids only, no token material.
			log.warn("Refresh token reuse: family {} of user {} revoked", current.getFamilyId(),
					current.getUser().getId());
			throw invalid();
		}
		if (!current.isLive(now)) {
			throw invalid();
		}

		current.markUsed(now);
		String successor = insert(current.getUser(), current.getFamilyId(), current.getFamilyExpiresAt(), now);

		// Built inside the transaction: the user is a lazy proxy and open-in-view is off.
		return new Rotation(AuthenticatedUser.from(current.getUser()), successor);
	}

	/**
	 * Logout. Idempotent on purpose: a missing or unknown cookie is not an error, the
	 * client ends up logged out either way.
	 */
	@Transactional
	public void revoke(String rawToken) {
		if (rawToken == null) {
			return;
		}
		Instant now = clock.instant();
		refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> revokeFamily(token.getFamilyId(), now));
	}

	private void revokeFamily(UUID familyId, Instant now) {
		for (RefreshToken live : refreshTokens.findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull(familyId)) {
			live.revoke(now);
		}
	}

	private String insert(AppUser user, UUID familyId, Instant familyExpiresAt, Instant now) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant idle = now.plus(props.refreshTtl());
		Instant expiresAt = idle.isBefore(familyExpiresAt) ? idle : familyExpiresAt;
		refreshTokens.save(new RefreshToken(user, familyId, hash(raw), expiresAt, familyExpiresAt));
		return raw;
	}

	/** Package-visible so a test can plant a row for a raw token it knows. */
	static String hash(String rawToken) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha256.digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
		}
	}

	private static UnauthorizedException invalid() {
		return new UnauthorizedException(ProblemType.INVALID_REFRESH_TOKEN, DETAIL);
	}

}
