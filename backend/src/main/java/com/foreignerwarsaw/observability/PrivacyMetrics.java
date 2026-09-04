package com.foreignerwarsaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §27/§79/§148 - personal-data export/deletion counters.
 * No email, user id, or payload content on any tag - these are pure counts (brief §27's own "no
 * email/user tags").
 *
 * <p>{@code account.deletion.completed} increments exactly once for a real, successful logical
 * deletion - a concurrent duplicate request cannot double-count it (brief §148) because {@link
 * com.foreignerwarsaw.user.account.AccountDeletionService#deleteOwnAccount} deletes the {@code
 * User} row itself: a second concurrent call re-authenticating against an already-deleted account
 * fails at the password check (the account no longer exists to look up), never reaching this
 * counter a second time.
 */
@Component
public class PrivacyMetrics {

  private final MeterRegistry meterRegistry;

  public PrivacyMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordExportCompleted() {
    increment("account.export.completed", "Personal-data exports successfully generated");
  }

  public void recordExportFailed() {
    increment("account.export.failed", "Personal-data export requests that failed unexpectedly");
  }

  public void recordDeletionCompleted() {
    increment("account.deletion.completed", "Accounts successfully, permanently deleted");
  }

  public void recordDeletionFailed() {
    increment(
        "account.deletion.failed",
        "Account deletion requests that failed unexpectedly (excludes expected reauthentication failures)");
  }

  private void increment(String name, String description) {
    Counter.builder(name).description(description).register(meterRegistry).increment();
  }
}
