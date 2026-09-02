package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;

/**
 * {@code requirementType = CONDITIONAL} is rendered by the frontend as "may be required depending
 * on your situation" (brief §68) - never as a claim the application has already evaluated the user
 * (brief §90: no eligibility engine exists yet).
 */
public record DocumentRequirementResponse(
    String code,
    String documentType,
    String name,
    String description,
    String requirementType,
    boolean requiredByDefault,
    Integer numberOfCopies,
    Boolean originalRequired,
    Boolean translationRequired,
    Boolean swornTranslationRequired,
    Boolean apostilleRequired,
    Boolean legalisationRequired,
    String validityPeriodDescription,
    String notes) {

  public static DocumentRequirementResponse from(DocumentRequirementVersion version) {
    var documentType = version.getDocumentRequirement().getDocumentType();
    return new DocumentRequirementResponse(
        version.getDocumentRequirement().getStableCode(),
        documentType != null ? documentType.getCode() : null,
        version.getName(),
        version.getDescription(),
        version.getRequirementType().name(),
        version.isRequiredByDefault(),
        version.getNumberOfCopies(),
        version.getOriginalRequired(),
        version.getTranslationRequired(),
        version.getSwornTranslationRequired(),
        version.getApostilleRequired(),
        version.getLegalisationRequired(),
        version.getValidityPeriodDescription(),
        version.getNotes());
  }
}
