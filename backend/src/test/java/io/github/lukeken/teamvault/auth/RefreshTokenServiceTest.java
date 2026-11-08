package io.github.lukeken.teamvault.auth;

import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.common.UnauthorizedException;
import io.github.lukeken.teamvault.user.AppUser;
import io.github.lukeken.teamvault.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.shaded.org.yaml.snakeyaml.events.Event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit rung: a fixed clock makes every expiry instant exact, the repository is a mock, and
// the hash is checked through the package-visible function the integration test uses too.
@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

	static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

	static final Duration IDLE = Duration.ofDays(14);

	static final Duration FAMILY = Duration.ofDays(30);

	@Mock
	private RefreshTokenRepository refreshTokens;

	@Mock
	private AppUserRepository users;

	private final AppUser alice = new AppUser("alice@acme.io", "$2a$10$hash", "Alice");

	private RefreshTokenService service;

	@BeforeEach
	void setup() {
		JwtProperties props = new JwtProperties("test-only-secret-that-is-at-least-32-characters-long",
				Duration.ofMinutes(15), IDLE, FAMILY, true);
		service = new RefreshTokenService(refreshTokens, users, props, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void issue_storesHashNotRawToken_expiryIsIdleTtl_familyCapSet() {
		UUID userId = UUID.randomUUID();
		when(users.getReferenceById(userId)).thenReturn(alice);

		String raw = service.issue(userId);

		RefreshToken saved = savedRow();

		assertThat(raw).hasSize(43); // 32 bytes, base64url, no padding
		assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
		assertThat(saved.getUser()).isSameAs(alice);
		assertThat(saved.getFamilyId()).isNotNull();
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(IDLE));
		assertThat(saved.getFamilyExpiresAt()).isEqualTo(NOW.plus(FAMILY));
		assertThat(saved.isLive(NOW)).isTrue();
	}

	@Test
	void rotate_liveToken_markedUsed_successorSharesFamilyAndCap() {
		UUID family = UUID.randomUUID();
		RefreshToken current = row(family, NOW.plus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(20)), "r1");
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("r1"))).thenReturn(Optional.of(current));

		RefreshTokenService.Rotation rotation = service.rotate("r1");

		assertThat(current.getUsedAt()).isEqualTo(NOW);
		assertThat(current.isLive(NOW)).isFalse();

		RefreshToken successor = savedRow();
		assertThat(successor.getFamilyId()).isEqualTo(family);
		assertThat(successor.getFamilyExpiresAt()).isEqualTo(current.getFamilyExpiresAt());
		// The idle lifetime ends before the family cap here, so idle wins.
		assertThat(successor.getExpiresAt()).isEqualTo(NOW.plus(IDLE));
		assertThat(successor.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken()));
		assertThat(rotation.refreshToken()).isNotEqualTo("r1");
		assertThat(rotation.user().email()).isEqualTo("alice@acme.io");
	}

	@Test
	void rotate_nearFamilyCap_successorExpiresAtTheCap() {
		Instant cap = NOW.plus(Duration.ofDays(3));
		RefreshToken current = row(UUID.randomUUID(), cap, cap, "r1");
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("r1"))).thenReturn(Optional.of(current));

		service.rotate("r1");

		assertThat(savedRow().getExpiresAt()).isEqualTo(cap);
	}

	@Test
	void rotate_usedToken_revokesTheFamilysLiveRow_401() {
		UUID family = UUID.randomUUID();
		RefreshToken used = row(family, NOW.plus(IDLE), NOW.plus(FAMILY), "r1");
		used.markUsed(NOW.minusSeconds(60));
		RefreshToken live = row(family, NOW.plus(IDLE), NOW.plus(FAMILY), "r2");
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("r1"))).thenReturn(Optional.of(used));
		when(refreshTokens.findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull(family)).thenReturn(List.of(live));

		assertThatThrownBy(() -> service.rotate("r1")).isInstanceOfSatisfying(UnauthorizedException.class,
				e -> assertThat(e.type()).isEqualTo(ProblemType.INVALID_REFRESH_TOKEN));

		assertThat(live.getRevokedAt()).isEqualTo(NOW);
		assertThat(live.isLive(NOW)).isFalse();
		verify(refreshTokens, never()).save(any());
	}

	@Test
	void rotate_revokedExpiredUnknownOrMissing_401_nothingWritten() {
		RefreshToken revoked = row(UUID.randomUUID(), NOW.plus(IDLE), NOW.plus(FAMILY), "revoked");
		revoked.revoke(NOW.minusSeconds(60));
		RefreshToken expired = row(UUID.randomUUID(), NOW.minusSeconds(1), NOW.plus(FAMILY), "expired");
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("revoked"))).thenReturn(Optional.of(revoked));
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("expired"))).thenReturn(Optional.of(expired));
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

		for (String raw : new String[] { "revoked", "expired", "unknown", null }) {
			assertThatThrownBy(() -> service.rotate(raw)).isInstanceOfSatisfying(UnauthorizedException.class,
					e -> assertThat(e.type()).isEqualTo(ProblemType.INVALID_REFRESH_TOKEN));
		}

		verify(refreshTokens, never()).save(any());
		verify(refreshTokens, never()).findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull(any());
	}

	@Test
	void revoke_knownToken_familysLiveRowRevoked_unknownOrMissingIgnored() {
		UUID family = UUID.randomUUID();
		RefreshToken live = row(family, NOW.plus(IDLE), NOW.plus(FAMILY), "r3");
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("r3"))).thenReturn(Optional.of(live));
		when(refreshTokens.findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull(family)).thenReturn(List.of(live));
		when(refreshTokens.findByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

		service.revoke("r3");
		service.revoke("unknown");
		service.revoke(null);

		assertThat(live.getRevokedAt()).isEqualTo(NOW);
		verify(refreshTokens, never()).save(any());
	}

	private RefreshToken row(UUID family, Instant expiresAt, Instant familyExpiresAt, String raw) {
		return new RefreshToken(alice, family, RefreshTokenService.hash(raw), expiresAt, familyExpiresAt);
	}

	private RefreshToken savedRow() {
		ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokens).save(saved.capture());
		return saved.getValue();
	}

}
