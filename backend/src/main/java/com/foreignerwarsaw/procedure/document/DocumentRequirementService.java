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
}
