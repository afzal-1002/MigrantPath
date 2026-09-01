package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Uses a mocked {@link Clock} (brief §36) so cooldown expiry is tested without sleeping. */
class RateLimiterTest {

  @Test
  void secondAttemptWithinCooldownIsRejected() {
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    RateLimiter rateLimiter = new RateLimiter(clock);

    assertThat(rateLimiter.tryAcquire("key", Duration.ofMinutes(1))).isTrue();
    assertThat(rateLimiter.tryAcquire("key", Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void attemptAfterCooldownElapsedIsAccepted() {
    Clock clock = mock(Clock.class);
    when(clock.instant())
        .thenReturn(Instant.parse("2026-01-01T00:00:00Z"))
        .thenReturn(Instant.parse("2026-01-01T00:02:00Z"));
    RateLimiter rateLimiter = new RateLimiter(clock);

    assertThat(rateLimiter.tryAcquire("key", Duration.ofMinutes(1))).isTrue();
    assertThat(rateLimiter.tryAcquire("key", Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void differentKeysDoNotShareACooldown() {
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    RateLimiter rateLimiter = new RateLimiter(clock);

    assertThat(rateLimiter.tryAcquire("key-a", Duration.ofMinutes(1))).isTrue();
    assertThat(rateLimiter.tryAcquire("key-b", Duration.ofMinutes(1))).isTrue();
  }
}
