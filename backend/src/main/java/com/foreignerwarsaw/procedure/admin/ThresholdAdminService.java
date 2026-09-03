package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdValueType;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code ProcedureAdminService}/{@code RuleAdminService} exactly. */
@Service
public class ThresholdAdminService {

  private final ThresholdService thresholdService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final AuditService auditService;

  public ThresholdAdminService(
      ThresholdService thresholdService,
      ContentReviewCoordinator reviewCoordinator,
      AuditService auditService) {
    this.thresholdService = thresholdService;
    this.reviewCoordinator = reviewCoordinator;
    this.auditService = auditService;
  }

  @Transactional
  public Threshold createThreshold(
      String code, String canonicalName, ThresholdValueType valueType, User actor) {
    Threshold threshold = thresholdService.createThreshold(code, canonicalName, valueType);
    auditService.record(
        actor,
        AuditActionType.THRESHOLD_CREATED,
        AuditEntityType.THRESHOLD,
        threshold.getId(),
        threshold.getCode(),
        null,
        "Created threshold " + threshold.getCode());
    return threshold;
  }

  @Transactional
  public ThresholdVersion createDraftVersion(
      String code, BigDecimal value, String valueText, User actor) {
    Threshold threshold = thresholdService.getByCode(code);
    ThresholdVersion version =
        thresholdService.createDraftVersion(threshold, value, valueText, actor);
    audit(actor, AuditActionType.THRESHOLD_VERSION_CREATED, version, "Created draft version");
    return version;
  }

  @Transactional
  public ThresholdVersion updateDraft(
      UUID versionId,
      BigDecimal value,
      String valueText,
      LocalDate effectiveFrom,
      String notes,
      User actor) {
    ThresholdVersion version =
        thresholdService.updateDraftContent(versionId, value, valueText, effectiveFrom, notes);
    audit(actor, AuditActionType.THRESHOLD_VERSION_UPDATED, version, "Updated draft value");
    return version;
  }

  @Transactional
  public ThresholdVersion submitForReview(UUID versionId, User actor) {
    ThresholdVersion version = thresholdService.submitForReview(versionId, actor);
    reviewCoordinator.openReview(AuditEntityType.THRESHOLD_VERSION, versionId, actor);
    audit(actor, AuditActionType.CONTENT_SUBMITTED, version, "Submitted version for review");
    return version;
  }

  @Transactional
  public ThresholdVersion approve(UUID versionId, User actor, String comment) {
    reviewCoordinator.approve(AuditEntityType.THRESHOLD_VERSION, versionId, actor, comment);
    ThresholdVersion version = thresholdService.approve(versionId, actor);
    audit(actor, AuditActionType.CONTENT_APPROVED, version, "Approved version");
    return version;
  }

  @Transactional
  public ThresholdVersion requestChanges(UUID versionId, User actor, String comment) {
    reviewCoordinator.requestChanges(AuditEntityType.THRESHOLD_VERSION, versionId, actor, comment);
    ThresholdVersion version = thresholdService.sendBackToDraft(versionId);
    audit(
        actor, AuditActionType.CONTENT_CHANGES_REQUESTED, version, "Requested changes: " + comment);
    return version;
  }

  @Transactional
  public ThresholdVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    ThresholdVersion version = thresholdService.publish(versionId, actor, effectiveFrom);
    audit(
        actor, AuditActionType.CONTENT_PUBLISHED, version, "Published effective " + effectiveFrom);
    return version;
  }

  @Transactional
  public ThresholdVersion archive(UUID versionId, User actor) {
    ThresholdVersion version = thresholdService.archive(versionId);
    audit(actor, AuditActionType.CONTENT_ARCHIVED, version, "Archived version");
    return version;
  }

  private void audit(User actor, AuditActionType type, ThresholdVersion version, String summary) {
    auditService.record(
        actor,
        type,
        AuditEntityType.THRESHOLD_VERSION,
        version.getId(),
        version.getThreshold().getCode(),
        version.getId(),
        summary);
  }
}
