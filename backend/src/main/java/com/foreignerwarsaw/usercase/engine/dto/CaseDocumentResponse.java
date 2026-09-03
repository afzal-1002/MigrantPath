package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import java.time.Instant;
import java.util.UUID;

public record CaseDocumentResponse(
    UUID id,
    String stableCode,
    String name,
    String description,
    String requirementType,
    String applicability,
    boolean mandatory,
    Integer numberOfCopies,
    Boolean originalRequired,
    Boolean translationRequired,
    Boolean swornTranslationRequired,
    Boolean apostilleRequired,
    Boolean legalisationRequired,
    String validityPeriodDescription,
    String contentNotes,
    String userNote,
    int sortOrder,
    String status,
    Instant readyAt) {

  public static CaseDocumentResponse from(UserCaseDocument document) {
    return new CaseDocumentResponse(
        document.getId(),
        document.getStableCode(),
        document.getNameSnapshot(),
        document.getDescriptionSnapshot(),
        document.getRequirementType().name(),
        document.getApplicability().name(),
        document.isMandatory(),
        document.getNumberOfCopiesSnapshot(),
        document.getOriginalRequiredSnapshot(),
        document.getTranslationRequiredSnapshot(),
        document.getSwornTranslationRequiredSnapshot(),
        document.getApostilleRequiredSnapshot(),
        document.getLegalisationRequiredSnapshot(),
        document.getValidityPeriodDescriptionSnapshot(),
        document.getContentNotesSnapshot(),
        document.getUserNote(),
        document.getSortOrder(),
        document.getStatus().name(),
        document.getReadyAt());
  }
}
