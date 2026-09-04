package com.foreignerwarsaw.observability;

import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §22/§23/§146 - one counter per Rule evaluation outcome,
 * plus a dedicated error counter with a bounded {@code errorCategory} tag (never the raw exception
 * message, brief §23's own "do not emit exception message as tag"). Counted once per Rule
 * evaluation, not once per {@code RecommendationRun} - a single run evaluating five applicable
 * Rules increments {@code rule.evaluation} five times (brief §146). {@code
 * rule.evaluation{result=ERROR}} and {@code rule.evaluation.error} are deliberately both
 * incremented on the same failure (brief §144's "define exactly once" - the first answers "how many
 * of every outcome", the second answers "what kind of error", not two disconnected totals).
 *
 * <p>Tags stay low-cardinality by construction: {@code result} is the four-value {@link
 * RuleEvaluationStatus} enum, {@code errorCategory} is the small, fixed {@link ErrorCategory} enum
 * below - never a rule code, rule version id, or any user fact (brief §18/§22).
 */
@Component
public class RuleMetrics {

  /**
   * A small, fixed, non-exhaustive-message error taxonomy (brief §23) - extend this enum, never the
   * tag value itself, if a genuinely new category is found.
   */
  public enum ErrorCategory {
    CONFIGURATION,
    THRESHOLD_RESOLUTION,
    FACT_RESOLUTION,
    UNKNOWN
  }

  private final MeterRegistry meterRegistry;

  public RuleMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordEvaluation(RuleEvaluationStatus status) {
    Counter.builder("rule.evaluation")
        .description("Rule evaluations, by outcome")
        .tag("result", status.name())
        .register(meterRegistry)
        .increment();
  }

  public void recordError(ErrorCategory category) {
    Counter.builder("rule.evaluation.error")
        .description("Rule evaluation errors, by category")
        .tag("errorCategory", category.name())
        .register(meterRegistry)
        .increment();
  }
}
