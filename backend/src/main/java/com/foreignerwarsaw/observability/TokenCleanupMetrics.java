package com.foreignerwarsaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §30/§70/§71/§149 - scheduled-job observability for
 * {@link com.foreignerwarsaw.auth.TokenCleanupService}, the exact job a real, previously-
 * undiscovered self-invocation bug (Phase 13) made throw on every real run in a deployed stack with
 * no signal anywhere that it was failing - these three counters are what would have made that bug
 * visible immediately instead of only when this project happened to test the real deployed stack.
 * No token id/hash/type on any tag (brief §30/§149).
 */
@Component
public class TokenCleanupMetrics {

  private final MeterRegistry meterRegistry;

  public TokenCleanupMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordRun() {
    increment("token.cleanup.run", "Scheduled token-cleanup executions, regardless of outcome");
  }

  public void recordFailure() {
    increment("token.cleanup.failure", "Scheduled token-cleanup executions that threw");
  }

  public void recordDeleted(int count) {
    if (count <= 0) {
      return;
    }
    Counter.builder("token.cleanup.deleted")
        .description("Expired/stale verification and password-reset tokens actually removed")
        .register(meterRegistry)
        .increment(count);
  }

  private void increment(String name, String description) {
    Counter.builder(name).description(description).register(meterRegistry).increment();
  }
}
