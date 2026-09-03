package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.core.UserCaseStep;
import java.time.Instant;
import java.util.UUID;

public record CaseStepResponse(
    UUID id,
    String stableCode,
    String title,
    String description,
    String detailedInstructions,
    String stepType,
    int sortOrder,
    boolean mandatory,
    String status,
    Instant completedAt) {

  public static CaseStepResponse from(UserCaseStep step) {
    return new CaseStepResponse(
        step.getId(),
        step.getStableCode(),
        step.getTitleSnapshot(),
        step.getDescriptionSnapshot(),
        step.getDetailedInstructionsSnapshot(),
        step.getStepType().name(),
        step.getSortOrder(),
        step.isMandatory(),
        step.getStatus().name(),
        step.getCompletedAt());
  }
}
