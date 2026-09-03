package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.threshold.ThresholdValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateThresholdRequest(
    @NotBlank String code, @NotBlank String canonicalName, @NotNull ThresholdValueType valueType) {}
