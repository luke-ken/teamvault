package io.github.lukeken.teamvault.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit rung: the sweep is one statement and one log line. The cron wiring itself is
// Spring's, not ours, so it is not under test here.
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
public class RefreshTokenCleanupTest {

	static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");

	@Mock
	private RefreshTokenRepository refreshTokens;

	@Test
	void sweep_deleteByExpiryAtTheClocksNow_logsTheCount(CapturedOutput output) {
		when(refreshTokens.deleteExpired(NOW)).thenReturn(3);

		new RefreshTokenCleanup(refreshTokens, Clock.fixed(NOW, ZoneOffset.UTC)).sweep();

		verify(refreshTokens).deleteExpired(NOW);
		assertThat(output).contains("Refresh token sweep: 3 expired rows deleted");
	}

}
