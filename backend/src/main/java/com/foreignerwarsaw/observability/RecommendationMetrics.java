package com.foreignerwarsaw.observability;

import com.foreignerwarsaw.recommendation.core.RecommendationRunStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §24/§25/§144/§145 - one counter per {@code
 * RecommendationRun} outcome. {@code recommendation.completed}/{@code .partial}/{@code .failed}
 * each increment exactly once per run (brief §144/§145 - never once per candidate/card within the
 * run). {@code recommendation.zero_candidates} is a separate, purely diagnostic counter (brief §25)
 * - a COMPLETED run that happened to find no matching procedure is not itself a failure, so it is
 * never folded into {@code .failed}.
 *
 * <p>No tag carries a user fact, candidate procedure code, or run id (brief §24 "tags limited to
 * status").
 */
@Component
public class RecommendationMetrics {

  private final MeterRegistry meterRegistry;

  public RecommendationMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordRun(RecommendationRunStatus status) {
    Counter.builder("recommendation." + statusSuffix(status))
        .description("Recommendation runs, by outcome")
        .register(meterRegistry)
        .increment();
  }

  public void recordZeroCandidates() {
    Counter.builder("recommendation.zero_candidates")
        .description(
            "Recommendation runs that produced no candidate at all (diagnostic, not necessarily a failure)")
        .register(meterRegistry)
        .increment();
  }

  private String statusSuffix(RecommendationRunStatus status) {
    return switch (status) {
      case COMPLETED -> "completed";
      case PARTIAL -> "partial";
      case FAILED -> "failed";
      case RUNNING ->
          throw new IllegalArgumentException(
              "recordRun must only be called with a run's final status (COMPLETED/PARTIAL/FAILED), never the transient RUNNING state");
    };
  }
}
