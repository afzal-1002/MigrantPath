package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.step.StepVersion;

public record StepResponse(
    String code,
    String title,
    String description,
    String detailedInstructions,
    String stepType,
    int sortOrder,
    boolean mandatory,
    Boolean onlineAvailable,
    Boolean requiresAppointment,
    String expectedUserAction) {

  public static StepResponse from(StepVersion step) {
    return new StepResponse(
        step.getProcedureStep().getStableCode(),
        step.getTitle(),
        step.getDescription(),
        step.getDetailedInstructions(),
        step.getStepType().name(),
        step.getSortOrder(),
        step.isMandatory(),
        step.getOnlineAvailable(),
        step.getRequiresAppointment(),
        step.getExpectedUserAction());
  }
}
