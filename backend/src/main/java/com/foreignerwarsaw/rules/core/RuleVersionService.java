package com.foreignerwarsaw.rules.core;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and mechanically transitioning {@link RuleVersion} drafts - mirrors {@code
 * ProcedureVersionService} exactly (brief §17/§55). {@link RulePublishingService} owns
 * publish-readiness validation, the threshold-reference sync, and the actual publish/archive side
 * effects, kept separate.
 */
@Service
public class RuleVersionService {

  private final RuleVersionRepository ruleVersionRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final Clock clock;

  public RuleVersionService(
      RuleVersionRepository ruleVersionRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      Clock clock) {
    this.ruleVersionRepository = ruleVersionRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.clock = clock;
  }

  @Transactional
  public void attachSource(RuleVersion version, OfficialSource source, SourceRole role) {
    ruleVersionSourceRepository.save(new RuleVersionSource(version, source, role));
  }

  @Transactional
  public RuleVersion createDraft(
      Rule rule, String conditionTree, String explanationKey, User createdBy) {
    int nextVersionNumber = ruleVersionRepository.findMaxVersionNumber(rule.getId()) + 1;
    RuleVersion version =
        RuleVersion.draft(rule, nextVersionNumber, conditionTree, explanationKey, createdBy);
    return ruleVersionRepository.save(version);
  }

  /**
   * "Create new version from current version" (brief §17, mirrors brief §108's procedure
   * equivalent) - copies the condition tree text and explanation key as a new DRAFT; {@link
   * RulePublishingService} recomputes {@code rule_threshold_references} from the tree at publish
   * time, so nothing needs copying there. Sources are deliberately NOT copied - a republished rule
   * must be re-justified against a source, not silently inherit the prior version's.
   */
  @Transactional
  public RuleVersion createDraftFrom(RuleVersion source, User createdBy) {
    return createDraft(
        source.getRule(), source.getConditionTree(), source.getExplanationKey(), createdBy);
  }

  @Transactional
  public RuleVersion updateDraftContent(
      UUID versionId, String conditionTree, String explanationKey) {
    RuleVersion version = getManagedById(versionId);
    version.updateDraftContent(conditionTree, explanationKey);
    return version;
  }

  @Transactional
  public RuleVersion submitForReview(UUID versionId, User actor) {
    RuleVersion version = getManagedById(versionId);
    version.submitForReview(actor, clock.instant());
    return version;
  }

  @Transactional
  public RuleVersion sendBackToDraft(UUID versionId) {
    RuleVersion version = getManagedById(versionId);
    version.sendBackToDraft();
    return version;
  }

  @Transactional
  public RuleVersion approve(UUID versionId, User actor) {
    RuleVersion version = getManagedById(versionId);
    version.approve(actor, clock.instant());
    return version;
  }

  @Transactional(readOnly = true)
  public RuleVersion getById(UUID id) {
    return getManagedById(id);
  }

  private RuleVersion getManagedById(UUID versionId) {
    return ruleVersionRepository
        .findByIdFetchingRule(versionId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "RULE_VERSION_NOT_FOUND",
                    "No version found for id " + versionId));
  }
}
