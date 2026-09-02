package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.document.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddDocumentRequirementRequest(
    @NotBlank String stableCode,
    String documentTypeCode,
    @NotBlank String name,
    String description,
    @NotNull RequirementType requirementType,
    boolean requiredByDefault,
    int sortOrder) {}
