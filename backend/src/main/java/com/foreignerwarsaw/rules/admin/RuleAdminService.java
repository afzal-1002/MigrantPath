package com.foreignerwarsaw.rules.admin;

import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionService;
import com.foreignerwarsaw.user.User;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code ProcedureAdminService} exactly - see its Javadoc for the design rationale. */
@Service
public class RuleAdminService {

  private final RuleService ruleService;
  private final RuleVersionService ruleVersionService;
  private final RulePublishingService rulePublishingService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final AuditService auditService;

  public RuleAdminService(
      RuleService ruleService,
      RuleVersionService ruleVersionService,
      RulePublishingService rulePublishingService,
      ContentReviewCoordinator reviewCoordinator,
      AuditService auditService) {
    this.ruleService = ruleService;
    this.ruleVersionService = ruleVersionService;
    this.rulePublishingService = rulePublishingService;
    this.reviewCoordinator = reviewCoordinator;
    this.auditService = auditService;
  }

  @Transactional
  public Rule createRule(
      String code,
      String canonicalName,
      com.foreignerwarsaw.rules.core.RuleType ruleType,
      com.foreignerwarsaw.rules.core.RuleTargetType targetType,
      String targetCode,
      User actor) {
    Rule rule = ruleService.createRule(code, canonicalName, ruleType, targetType, targetCode);
    auditService.record(
        actor,
        AuditActionType.RULE_CREATED,
        AuditEntityType.RULE,
        rule.getId(),
        rule.getCode(),
        null,
        "Created rule " + rule.getCode());
    return rule;
  }

  @Transactional
  public RuleVersion createDraftVersion(
      String code, String conditionTree, String explanationKey, User actor) {
    Rule rule = ruleService.getByCode(code);
    RuleVersion version =
        ruleVersionService.createDraft(rule, conditionTree, explanationKey, actor);
    audit(actor, AuditActionType.RULE_VERSION_CREATED, version, "Created draft version");
    return version;
  }

  @Transactional
  public RuleVersion createDraftFrom(UUID sourceVersionId, User actor) {
    RuleVersion source = ruleVersionService.getById(sourceVersionId);
    RuleVersion copy = ruleVersionService.createDraftFrom(source, actor);
    audit(
        actor,
        AuditActionType.RULE_VERSION_CREATED,
        copy,
        "Created draft version "
            + copy.getVersionNumber()
            + " copied from version "
            + source.getVersionNumber());
    return copy;
  }

  @Transactional
  public RuleVersion updateDraft(
      UUID versionId,
      String conditionTree,
      String explanationKey,
      String changeSummary,
      User actor) {
    RuleVersion version =
        ruleVersionService.updateDraftContent(
            versionId, conditionTree, explanationKey, changeSummary);
    audit(actor, AuditActionType.RULE_VERSION_UPDATED, version, "Updated draft condition tree");
    return version;
  }

  @Transactional
  public RuleVersion submitForReview(UUID versionId, User actor) {
    RuleVersion version = ruleVersionService.submitForReview(versionId, actor);
    reviewCoordinator.openReview(AuditEntityType.RULE_VERSION, versionId, actor);
    audit(actor, AuditActionType.CONTENT_SUBMITTED, version, "Submitted version for review");
    return version;
  }

  @Transactional
  public RuleVersion approve(UUID versionId, User actor, String comment) {
    reviewCoordinator.approve(AuditEntityType.RULE_VERSION, versionId, actor, comment);
    RuleVersion version = ruleVersionService.approve(versionId, actor);
    audit(actor, AuditActionType.CONTENT_APPROVED, version, "Approved version");
    return version;
  }

  @Transactional
  public RuleVersion requestChanges(UUID versionId, User actor, String comment) {
    reviewCoordinator.requestChanges(AuditEntityType.RULE_VERSION, versionId, actor, comment);
    RuleVersion version = ruleVersionService.sendBackToDraft(versionId);
    audit(
        actor, AuditActionType.CONTENT_CHANGES_REQUESTED, version, "Requested changes: " + comment);
    return version;
  }

  @Transactional
  public RuleVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    RuleVersion version = rulePublishingService.publish(versionId, actor, effectiveFrom);
    audit(
        actor, AuditActionType.CONTENT_PUBLISHED, version, "Published effective " + effectiveFrom);
    return version;
  }

  @Transactional
  public RuleVersion archive(UUID versionId, User actor) {
    RuleVersion version = rulePublishingService.archive(versionId);
    audit(actor, AuditActionType.CONTENT_ARCHIVED, version, "Archived version");
    return version;
  }

  private void audit(User actor, AuditActionType type, RuleVersion version, String summary) {
    auditService.record(
        actor,
        type,
        AuditEntityType.RULE_VERSION,
        version.getId(),
        version.getRule().getCode(),
        version.getId(),
        summary);
  }
}
