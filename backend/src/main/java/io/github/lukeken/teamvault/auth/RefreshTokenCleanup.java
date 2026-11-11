package io.github.lukeken.teamvault.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Nightly sweep of expired refresh tokens (ADR-007). Status is irrelevant: an expired row
 * can do nothing whatever its stamps say. Kept out of login on purpose, so login does one
 * job and a failing delete cannot fail a login. Cron rather than a fixed rate: the wall
 * clock survives restarts, an interval would start over with every deploy. Two instances
 * running it is harmless, the delete is idempotent.
 */
@Component
public class RefreshTokenCleanup {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

	private final RefreshTokenRepository refreshTokens;

	private final Clock clock;

	public RefreshTokenCleanup(RefreshTokenRepository refreshTokens, Clock clock) {
		this.refreshTokens = refreshTokens;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
	@Transactional
	public void sweep() {
		int deleted = refreshTokens.deleteExpired(clock.instant());
		log.info("Refresh token sweep: {} expired rows deleted", deleted);
	}

}
