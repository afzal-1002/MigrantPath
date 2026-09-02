package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.core.JurisdictionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProcedureRequest(
    @NotBlank String code,
    @NotBlank String categoryCode,
    @NotBlank String canonicalName,
    String shortDescription,
    @NotNull JurisdictionScope jurisdictionScope) {}
