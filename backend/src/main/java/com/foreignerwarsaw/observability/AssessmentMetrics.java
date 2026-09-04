package com.foreignerwarsaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §20/§21 - {@code assessment.completed}, incremented
 * exactly once per {@code Assessment} transitioning to {@code COMPLETED} ({@link
 * com.foreignerwarsaw.questionnaire.assessment.AssessmentCompletionService#complete} only ever
 * calls this after a successful completion, never on the {@code ASSESSMENT_INCOMPLETE}/ {@code
 * ASSESSMENT_NOT_IN_PROGRESS} expected-error paths). No tag - never by answer content (brief §21's
 * own "do not tag by answers").
 */
@Component
public class AssessmentMetrics {

  private final MeterRegistry meterRegistry;

  public AssessmentMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordCompleted() {
    Counter.builder("assessment.completed")
        .description("Assessments successfully completed")
        .register(meterRegistry)
        .increment();
  }
}
