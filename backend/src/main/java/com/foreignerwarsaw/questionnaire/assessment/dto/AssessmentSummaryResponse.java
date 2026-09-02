package com.foreignerwarsaw.questionnaire.assessment.dto;

import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal per-assessment row for a dashboard "resume/completed" check (brief §56) - never embeds
 * answers.
 */
public record AssessmentSummaryResponse(
    UUID id, String status, String questionnaireCode, Instant startedAt, Instant completedAt) {

  public static AssessmentSummaryResponse from(Assessment assessment) {
    return new AssessmentSummaryResponse(
        assessment.getId(),
        assessment.getStatus().name(),
        assessment.getQuestionnaire().getCode(),
        assessment.getStartedAt(),
        assessment.getCompletedAt());
  }
}
