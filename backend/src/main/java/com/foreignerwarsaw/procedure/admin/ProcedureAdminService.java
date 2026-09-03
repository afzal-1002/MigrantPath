package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedurePublishingService;
import com.foreignerwarsaw.procedure.core.ProcedureService;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionService;
import com.foreignerwarsaw.user.User;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The review-workflow bookkeeping layer over {@link ProcedureVersionService}/{@link
 * ProcedurePublishingService} (brief §5/§6) - every submit/approve/request-changes/publish/archive
 * action goes through here so that {@link ContentReviewCoordinator}'s self-approval check and
 * {@link AuditService}'s audit trail are applied uniformly, without duplicating either concern
 * inside {@code ProcedureVersionService} itself. Content mutation (create/edit draft,
 * steps/documents/fees) stays a thin pass-through to the existing Phase 4 services - no new
 * validation is introduced there.
 */
@Service
public class ProcedureAdminService {

  private final ProcedureService procedureService;
  private final ProcedureVersionService procedureVersionService;
  private final ProcedurePublishingService procedurePublishingService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final AuditService auditService;

  public ProcedureAdminService(
      ProcedureService procedureService,
      ProcedureVersionService procedureVersionService,
      ProcedurePublishingService procedurePublishingService,
      ContentReviewCoordinator reviewCoordinator,
      AuditService auditService) {
    this.procedureService = procedureService;
    this.procedureVersionService = procedureVersionService;
    this.procedurePublishingService = procedurePublishingService;
    this.reviewCoordinator = reviewCoordinator;
    this.auditService = auditService;
  }

  @Transactional
  public Procedure createProcedure(
      String code,
      String categoryCode,
      String canonicalName,
      String shortDescription,
      com.foreignerwarsaw.procedure.core.JurisdictionScope scope,
      User actor) {
    Procedure procedure =
        procedureService.createProcedure(
            code, categoryCode, canonicalName, shortDescription, scope);
    auditService.record(
        actor,
        AuditActionType.PROCEDURE_CREATED,
        AuditEntityType.PROCEDURE,
        procedure.getId(),
        procedure.getCode(),
        null,
        "Created procedure " + procedure.getCode());
    return procedure;
  }

  @Transactional
  public ProcedureVersion createDraftVersion(
      String code, String title, String summary, String description, User actor) {
    Procedure procedure = procedureService.getByCode(code);
    ProcedureVersion version =
        procedureVersionService.createDraft(procedure, title, summary, description, actor);
    audit(actor, AuditActionType.PROCEDURE_VERSION_CREATED, version, "Created draft version");
    return version;
  }

  @Transactional
  public ProcedureVersion createDraftFrom(UUID sourceVersionId, User actor) {
    ProcedureVersion source = procedureVersionService.getById(sourceVersionId);
    ProcedureVersion copy = procedureVersionService.createDraftFrom(source, actor);
    audit(
        actor,
        AuditActionType.PROCEDURE_VERSION_CREATED,
        copy,
        "Created draft version "
            + copy.getVersionNumber()
            + " copied from version "
            + source.getVersionNumber());
    return copy;
  }

  @Transactional
  public ProcedureVersion updateDraft(
      UUID versionId,
      String title,
      String summary,
      String description,
      LocalDate effectiveFrom,
      String changeSummary,
      User actor) {
    requireDraft(versionId);
    ProcedureVersion version =
        procedureVersionService.updateDraftContent(
            versionId, title, summary, description, effectiveFrom, changeSummary);
    audit(actor, AuditActionType.PROCEDURE_VERSION_UPDATED, version, "Updated draft overview");
    return version;
  }

  @Transactional
  public ProcedureVersion submitForReview(UUID versionId, User actor) {
    ProcedureVersion version = procedureVersionService.submitForReview(versionId, actor);
    reviewCoordinator.openReview(AuditEntityType.PROCEDURE_VERSION, versionId, actor);
    audit(
        actor,
        AuditActionType.CONTENT_SUBMITTED,
        version,
        "Submitted version " + version.getVersionNumber() + " for review");
    return version;
  }

  @Transactional
  public ProcedureVersion approve(UUID versionId, User actor, String comment) {
    reviewCoordinator.approve(AuditEntityType.PROCEDURE_VERSION, versionId, actor, comment);
    ProcedureVersion version = procedureVersionService.approve(versionId, actor);
    audit(
        actor,
        AuditActionType.CONTENT_APPROVED,
        version,
        "Approved version " + version.getVersionNumber());
    return version;
  }

  @Transactional
  public ProcedureVersion requestChanges(UUID versionId, User actor, String comment) {
    reviewCoordinator.requestChanges(AuditEntityType.PROCEDURE_VERSION, versionId, actor, comment);
    ProcedureVersion version = procedureVersionService.sendBackToDraft(versionId);
    audit(
        actor,
        AuditActionType.CONTENT_CHANGES_REQUESTED,
        version,
        "Requested changes on version " + version.getVersionNumber() + ": " + comment);
    return version;
  }

  @Transactional
  public ProcedureVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    ProcedureVersion version = procedurePublishingService.publish(versionId, actor, effectiveFrom);
    audit(
        actor,
        AuditActionType.CONTENT_PUBLISHED,
        version,
        "Published version " + version.getVersionNumber() + " effective " + effectiveFrom);
    return version;
  }

  @Transactional
  public ProcedureVersion archive(UUID versionId, User actor) {
    ProcedureVersion version = procedurePublishingService.archive(versionId);
    audit(
        actor,
        AuditActionType.CONTENT_ARCHIVED,
        version,
        "Archived version " + version.getVersionNumber());
    return version;
  }

  private void requireDraft(UUID versionId) {
    ProcedureVersion version = procedureVersionService.getById(versionId);
    if (version.getStatus() != com.foreignerwarsaw.procedure.PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "VERSION_NOT_DRAFT",
          "Only a DRAFT version's overview can be edited");
    }
  }

  private void audit(User actor, AuditActionType type, ProcedureVersion version, String summary) {
    auditService.record(
        actor,
        type,
        AuditEntityType.PROCEDURE_VERSION,
        version.getId(),
        version.getProcedure().getCode(),
        version.getId(),
        summary);
  }
}
