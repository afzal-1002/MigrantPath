package com.foreignerwarsaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §26/§147/§148 - case lifecycle counters. No case id,
 * procedure code, or user id on any tag.
 *
 * <p>{@code case.creation}: incremented only on the branch that actually inserts a new {@code
 * UserCase} row - {@link com.foreignerwarsaw.usercase.engine.UserCaseCreationService}'s own
 * idempotent-duplicate-request early return (brief §147) never reaches this call, by construction
 * (the call site is placed after that check, not before it).
 *
 * <p>Named {@code case.creation}, not {@code case.created} - a real naming collision this phase
 * found via a production-like verification pass (docs/product/PHASE_14_REPORT.md): the modern
 * Prometheus client library's naming sanitizer ({@code
 * io.prometheus.metrics.model.snapshots.PrometheusNaming}, used transitively by {@code
 * micrometer-registry-prometheus}) strips a trailing {@code _created} segment from any metric name
 * - it is a reserved OpenMetrics suffix denoting a counter's own creation timestamp - so {@code
 * case.created} silently exported as bare {@code case_total} instead of {@code case_created_total},
 * with no error or warning anywhere. Confirmed empirically (a real HTTP case creation against a
 * live instance, then diffing {@code /actuator/prometheus} output) before picking this replacement
 * name; {@code case.creation} sanitizes cleanly to {@code case_creation_total}.
 *
 * <p>{@code case.upgrade}/{@code case.upgrade.failed}: an expected 409 (already-current, wrong
 * status) is a normal client-visible outcome, not an operational failure (brief §14's own "expected
 * 4xx do not create noisy ERROR logs") - {@code .failed} counts only a genuinely unexpected
 * exception during the upgrade transaction.
 */
@Component
public class CaseMetrics {

  private final MeterRegistry meterRegistry;

  public CaseMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordCaseCreated() {
    increment("case.creation", "New UserCase rows actually created (idempotent repeats excluded)");
  }

  public void recordCaseUpgrade() {
    increment("case.upgrade", "Successful case upgrades to the current procedure content");
  }

  public void recordCaseUpgradeFailed() {
    increment(
        "case.upgrade.failed",
        "Case upgrades that failed unexpectedly (excludes expected 409 conflicts)");
  }

  private void increment(String name, String description) {
    Counter.builder(name).description(description).register(meterRegistry).increment();
  }
}
