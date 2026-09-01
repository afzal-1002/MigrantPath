package com.foreignerwarsaw.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A deliberately simple, single-instance, in-memory cooldown tracker (brief §22: "an
 * application-level limiter is acceptable for initial single-instance deployment... design so it
 * can later be replaced by gateway/Redis-based limits"). Used for
 * registration/resend-verification/forgot-password/reset-password cooldowns - login brute-force
 * protection is a separate, persistent mechanism ({@link
 * com.foreignerwarsaw.user.User#incrementFailedLoginAttempts}) because a login lockout must survive
 * an application restart and be visible across instances, while an email cooldown does not need
 * either property.
 *
 * <p>Not bounded/evicted in Phase 2 - acceptable at MVP scale (a Map entry per distinct key
 * touched), revisited if it ever becomes a real memory concern.
 */
@Component
public class RateLimiter {

  private final Map<String, Instant> lastAttemptAt = new ConcurrentHashMap<>();
  private final Clock clock;

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * Returns {@code true} and records the attempt if {@code key} is not within its cooldown window;
   * returns {@code false} without recording anything otherwise.
   */
  public boolean tryAcquire(String key, Duration cooldown) {
    Instant now = clock.instant();
    Instant[] recorded = new Instant[1];
    lastAttemptAt.compute(
        key,
        (k, previous) -> {
          if (previous != null && previous.plus(cooldown).isAfter(now)) {
            recorded[0] = previous;
            return previous;
          }
          recorded[0] = null;
          return now;
        });
    return recorded[0] == null;
  }
}
