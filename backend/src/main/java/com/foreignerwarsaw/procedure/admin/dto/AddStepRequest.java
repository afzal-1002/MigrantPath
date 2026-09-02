package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.step.StepType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddStepRequest(
    @NotBlank String stableCode,
    @NotBlank String title,
    String description,
    @NotNull StepType stepType,
    int sortOrder,
    boolean mandatory) {}
