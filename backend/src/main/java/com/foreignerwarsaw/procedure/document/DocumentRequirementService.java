package com.foreignerwarsaw.procedure.document;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequirementService {

  private final DocumentRequirementRepository documentRequirementRepository;
  private final DocumentRequirementVersionRepository documentRequirementVersionRepository;

  public DocumentRequirementService(
      DocumentRequirementRepository documentRequirementRepository,
      DocumentRequirementVersionRepository documentRequirementVersionRepository) {
    this.documentRequirementRepository = documentRequirementRepository;
    this.documentRequirementVersionRepository = documentRequirementVersionRepository;
  }

  @Transactional
  public DocumentRequirementVersion addRequirement(
      ProcedureVersion procedureVersion,
      String stableCode,
      DocumentType documentType,
      String name,
      String description,
      RequirementType requirementType,
      boolean requiredByDefault,
      int sortOrder) {
    if (procedureVersion.getStatus() != PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "VERSION_NOT_DRAFT",
          "Document requirements can only be added to a DRAFT version");
    }
    Procedure procedure = procedureVersion.getProcedure();
    DocumentRequirement requirement =
        documentRequirementRepository
            .findByProcedure_IdAndStableCode(procedure.getId(), stableCode)
            .orElseGet(
                () ->
                    documentRequirementRepository.save(
                        new DocumentRequirement(procedure, stableCode, documentType)));
    DocumentRequirementVersion version =
        new DocumentRequirementVersion(
            requirement,
            procedureVersion,
            name,
            description,
            requirementType,
            requiredByDefault,
            sortOrder);
    return documentRequirementVersionRepository.save(version);
  }

  @Transactional(readOnly = true)
  public List<DocumentRequirementVersion> listForVersion(UUID procedureVersionId) {
    return documentRequirementVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(
        procedureVersionId);
  }

  /** Phase 9 addition (brief §23) - editing a requirement already on a still-DRAFT version. */
  @Transactional
  public DocumentRequirementVersion updateRequirement(
      UUID documentRequirementVersionId,
      String name,
      String description,
      RequirementType requirementType,
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
      int sortOrder) {
    DocumentRequirementVersion version = getManagedById(documentRequirementVersionId);
    requireDraft(version.getProcedureVersion());
    version.update(
        name,
        description,
        requirementType,
        requiredByDefault,
        numberOfCopies,
        originalRequired,
        copyRequired,
        translationRequired,
        swornTranslationRequired,
        apostilleRequired,
        legalisationRequired,
        validityPeriodDescription,
        notes,
        sortOrder);
    return version;
  }

  /** Phase 9 addition (brief §23) - removing a requirement from a still-DRAFT version. */
  @Transactional
  public void removeRequirement(UUID documentRequirementVersionId) {
    DocumentRequirementVersion version = getManagedById(documentRequirementVersionId);
    requireDraft(version.getProcedureVersion());
    documentRequirementVersionRepository.delete(version);
  }

  private DocumentRequirementVersion getManagedById(UUID id) {
    return documentRequirementVersionRepository
        .findByIdFetchingAll(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DOCUMENT_REQUIREMENT_NOT_FOUND",
                    "No document requirement found for id " + id));
  }

  private void requireDraft(ProcedureVersion procedureVersion) {
    if (procedureVersion.getStatus() != PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "VERSION_NOT_DRAFT",
          "Document requirements can only be edited on a DRAFT version");
    }
  }
}
