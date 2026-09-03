package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.document.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentRequirementRequest(
    @NotBlank String name,
    String description,
    @NotNull RequirementType requirementType,
    boolean requiredByDefault,
    Integer numberOfCopies,
    Boolean originalRequired,
    Boolean copyRequired,
    Boolean translationRequired,
    Boolean swornTranslationRequired,
    Boolean apostilleRequired,
    Boolean legalisationRequired,
    String validityPeriodDescription,
    String notes,
    int sortOrder) {}
