package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersionRepository;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
import com.foreignerwarsaw.procedure.fee.FeeVersionRepository;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.step.StepVersion;
import com.foreignerwarsaw.procedure.step.StepVersionRepository;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and mechanically transitioning {@link ProcedureVersion} drafts - {@link
 * ProcedurePublishingService} owns publish-readiness validation and the actual publish/archive side
 * effects, kept separate (brief §55/§56: cohesive services, not one giant class).
 */
@Service
public class ProcedureVersionService {

  private final ProcedureVersionRepository procedureVersionRepository;
  private final StepVersionRepository stepVersionRepository;
  private final DocumentRequirementVersionRepository documentRequirementVersionRepository;
  private final FeeVersionRepository feeVersionRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final Clock clock;

  public ProcedureVersionService(
      ProcedureVersionRepository procedureVersionRepository,
      StepVersionRepository stepVersionRepository,
      DocumentRequirementVersionRepository documentRequirementVersionRepository,
      FeeVersionRepository feeVersionRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      Clock clock) {
    this.procedureVersionRepository = procedureVersionRepository;
    this.stepVersionRepository = stepVersionRepository;
    this.documentRequirementVersionRepository = documentRequirementVersionRepository;
    this.feeVersionRepository = feeVersionRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.clock = clock;
  }

  @Transactional
  public void attachSource(ProcedureVersion version, OfficialSource source, SourceRole role) {
    procedureVersionSourceRepository.save(new ProcedureVersionSource(version, source, role));
  }

  @Transactional
  public ProcedureVersion createDraft(
      Procedure procedure, String title, String summary, String description, User createdBy) {
    int nextVersionNumber = procedureVersionRepository.findMaxVersionNumber(procedure.getId()) + 1;
    ProcedureVersion version =
        ProcedureVersion.draft(
            procedure, nextVersionNumber, title, summary, description, createdBy);
    return procedureVersionRepository.save(version);
  }

  /**
   * "Create new version from current version" (brief §108) - copies steps/documents/fees with new
   * IDs and a new DRAFT status; modifying the new draft never mutates the source version (proven by
   * {@code ProcedureVersioningIntegrationTest}).
   */
  @Transactional
  public ProcedureVersion createDraftFrom(ProcedureVersion source, User createdBy) {
    ProcedureVersion copy =
        createDraft(
            source.getProcedure(),
            source.getTitle(),
            source.getSummary(),
            source.getDescription(),
            createdBy);
    for (StepVersion step :
        stepVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(source.getId())) {
      StepVersion copiedStep =
          new StepVersion(
              step.getProcedureStep(),
              copy,
              step.getTitle(),
              step.getDescription(),
              step.getStepType(),
              step.getSortOrder(),
              step.isMandatory());
      stepVersionRepository.save(copiedStep);
    }
    for (DocumentRequirementVersion doc :
        documentRequirementVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(
            source.getId())) {
      DocumentRequirementVersion copiedDoc =
          new DocumentRequirementVersion(
              doc.getDocumentRequirement(),
              copy,
              doc.getName(),
              doc.getDescription(),
              doc.getRequirementType(),
              doc.isRequiredByDefault(),
              doc.getSortOrder());
      documentRequirementVersionRepository.save(copiedDoc);
    }
    for (FeeVersion fee : feeVersionRepository.findByProcedureVersion_Id(source.getId())) {
      FeeVersion copiedFee = new FeeVersion(fee.getFee(), copy, fee.getAmount(), fee.getCurrency());
      feeVersionRepository.save(copiedFee);
    }
    return copy;
  }

  @Transactional
  public ProcedureVersion updateDraftContent(
      UUID versionId, String title, String summary, String description) {
    ProcedureVersion version = getManagedById(versionId);
    version.updateDraftContent(title, summary, description, version.getEffectiveFrom());
    return version;
  }

  /**
   * Takes the version's {@code id}, not an already-loaded {@link ProcedureVersion} instance -
   * deliberately, and re-fetches inside this method's own transaction. A caller that loaded the
   * entity in an earlier (already-committed, now-closed) transaction and passed that instance in
   * would be handing this method a <b>detached</b> entity: mutating a detached entity's fields
   * changes the in-memory Java object but is never flushed to the database, since it's not managed
   * by this transaction's persistence context - a real bug this exact signature was introduced to
   * fix (found via {@code ProcedureVersioningIntegrationTest}'s full HTTP-level lifecycle test:
   * {@code approve} silently saw the pre-{@code submit} status because {@code submit}'s mutation
   * was never actually persisted).
   */
  @Transactional
  public ProcedureVersion submitForReview(UUID versionId, User actor) {
    ProcedureVersion version = getManagedById(versionId);
    version.submitForReview(actor, clock.instant());
    return version;
  }

  @Transactional
  public ProcedureVersion sendBackToDraft(UUID versionId) {
    ProcedureVersion version = getManagedById(versionId);
    version.sendBackToDraft();
    return version;
  }

  @Transactional
  public ProcedureVersion approve(UUID versionId, User actor) {
    ProcedureVersion version = getManagedById(versionId);
    version.approve(actor, clock.instant());
    return version;
  }

  private ProcedureVersion getManagedById(UUID versionId) {
    return procedureVersionRepository
        .findByIdFetchingProcedure(versionId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROCEDURE_VERSION_NOT_FOUND",
                    "No version found for id " + versionId));
  }

  @Transactional(readOnly = true)
  public ProcedureVersion getByProcedureAndVersionNumber(Procedure procedure, int versionNumber) {
    return procedureVersionRepository
        .findByProcedure_IdAndVersionNumber(procedure.getId(), versionNumber)
        .orElseThrow(
            () -> new ProcedureVersionNotFoundException(procedure.getCode(), versionNumber));
  }

  @Transactional(readOnly = true)
  public ProcedureVersion getById(UUID id) {
    return procedureVersionRepository
        .findByIdFetchingProcedure(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROCEDURE_VERSION_NOT_FOUND",
                    "No version found for id " + id));
  }
}
