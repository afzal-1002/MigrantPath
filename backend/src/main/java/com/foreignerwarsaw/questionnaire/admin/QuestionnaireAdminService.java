package com.foreignerwarsaw.questionnaire.admin;

import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersionService;
import com.foreignerwarsaw.user.User;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code ProcedureAdminService}/{@code RuleAdminService}/{@code ThresholdAdminService}. */
@Service
public class QuestionnaireAdminService {

  private final QuestionnaireVersionService questionnaireVersionService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final AuditService auditService;

  public QuestionnaireAdminService(
      QuestionnaireVersionService questionnaireVersionService,
      ContentReviewCoordinator reviewCoordinator,
      AuditService auditService) {
    this.questionnaireVersionService = questionnaireVersionService;
    this.reviewCoordinator = reviewCoordinator;
    this.auditService = auditService;
  }

  @Transactional
  public QuestionnaireVersion createDraftFrom(
      QuestionnaireVersion source, String title, String description, User actor) {
    QuestionnaireVersion copy =
        questionnaireVersionService.createDraftFrom(source, title, description, actor);
    audit(
        actor,
        AuditActionType.QUESTIONNAIRE_VERSION_CREATED,
        copy,
        "Created draft version "
            + copy.getVersionNumber()
            + " copied from version "
            + source.getVersionNumber());
    return copy;
  }

  @Transactional
  public QuestionnaireVersion updateDraft(
      UUID versionId, String title, String description, User actor) {
    QuestionnaireVersion version =
        questionnaireVersionService.updateDraftContent(versionId, title, description);
    audit(actor, AuditActionType.PROCEDURE_VERSION_UPDATED, version, "Updated draft overview");
    return version;
  }

  @Transactional
  public QuestionnaireVersion submitForReview(UUID versionId, User actor) {
    QuestionnaireVersion version = questionnaireVersionService.submitForReview(versionId, actor);
    reviewCoordinator.openReview(AuditEntityType.QUESTIONNAIRE_VERSION, versionId, actor);
    audit(actor, AuditActionType.CONTENT_SUBMITTED, version, "Submitted version for review");
    return version;
  }

  @Transactional
  public QuestionnaireVersion approve(UUID versionId, User actor, String comment) {
    reviewCoordinator.approve(AuditEntityType.QUESTIONNAIRE_VERSION, versionId, actor, comment);
    QuestionnaireVersion version = questionnaireVersionService.approve(versionId, actor);
    audit(actor, AuditActionType.CONTENT_APPROVED, version, "Approved version");
    return version;
  }

  @Transactional
  public QuestionnaireVersion requestChanges(UUID versionId, User actor, String comment) {
    reviewCoordinator.requestChanges(
        AuditEntityType.QUESTIONNAIRE_VERSION, versionId, actor, comment);
    QuestionnaireVersion version = questionnaireVersionService.sendBackToDraft(versionId);
    audit(
        actor, AuditActionType.CONTENT_CHANGES_REQUESTED, version, "Requested changes: " + comment);
    return version;
  }

  @Transactional
  public QuestionnaireVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    QuestionnaireVersion version =
        questionnaireVersionService.publish(versionId, actor, effectiveFrom);
    audit(
        actor, AuditActionType.CONTENT_PUBLISHED, version, "Published effective " + effectiveFrom);
    return version;
  }

  @Transactional
  public QuestionnaireVersion archive(UUID versionId, User actor) {
    QuestionnaireVersion version = questionnaireVersionService.archive(versionId);
    audit(actor, AuditActionType.CONTENT_ARCHIVED, version, "Archived version");
    return version;
  }

  private void audit(
      User actor, AuditActionType type, QuestionnaireVersion version, String summary) {
    auditService.record(
        actor,
        type,
        AuditEntityType.QUESTIONNAIRE_VERSION,
        version.getId(),
        version.getQuestionnaire().getCode(),
        version.getId(),
        summary);
  }
}
