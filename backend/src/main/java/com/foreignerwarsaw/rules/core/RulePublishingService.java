package com.foreignerwarsaw.rules.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.admin.validation.ValidationIssue;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import com.foreignerwarsaw.rules.condition.AllNode;
import com.foreignerwarsaw.rules.condition.AnyNode;
import com.foreignerwarsaw.rules.condition.ConditionNode;
import com.foreignerwarsaw.rules.condition.ConditionTreeParser;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidationException;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidator;
import com.foreignerwarsaw.rules.condition.LeafCondition;
import com.foreignerwarsaw.rules.condition.NotNode;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publish-time validation (brief §23/§27) and the transactional publish step, mirroring {@code
 * ProcedurePublishingService} exactly (validate -&gt; close the previous active version if needed
 * -&gt; sync {@code rule_threshold_references} -&gt; mark PUBLISHED, all one transaction or none of
 * it). The only place a {@link RuleVersion} is allowed to reach {@link PublicationStatus#PUBLISHED}
 * - {@link RuleVersion#markPublished} itself only checks the mechanical state-machine transition,
 * not readiness.
 */
@Service
public class RulePublishingService {

  private final RuleVersionRepository ruleVersionRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final RuleThresholdReferenceRepository ruleThresholdReferenceRepository;
  private final ConditionTreeValidator conditionTreeValidator;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public RulePublishingService(
      RuleVersionRepository ruleVersionRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      RuleThresholdReferenceRepository ruleThresholdReferenceRepository,
      ConditionTreeValidator conditionTreeValidator,
      ObjectMapper objectMapper,
      Clock clock) {
    this.ruleVersionRepository = ruleVersionRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.ruleThresholdReferenceRepository = ruleThresholdReferenceRepository;
    this.conditionTreeValidator = conditionTreeValidator;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Takes {@code versionId}, not an already-loaded entity, and re-fetches inside this transaction -
   * see {@code ProcedureVersionService#submitForReview}'s Javadoc for the detached-entity bug this
   * signature avoids.
   */
  @Transactional
  public RuleVersion publish(UUID versionId, User actor, LocalDate effectiveFrom) {
    RuleVersion version = getManagedById(versionId);
    validatePublishReadiness(version, effectiveFrom);

    UUID ruleId = version.getRule().getId();
    List<RuleVersion> currentlyPublished = ruleVersionRepository.findPublishedVersions(ruleId);
    for (RuleVersion existing : currentlyPublished) {
      if (existing.getId().equals(version.getId())) {
        continue;
      }
      if (existing.getEffectiveFrom() != null
          && !existing.getEffectiveFrom().isBefore(effectiveFrom)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "OVERLAPPING_PUBLISHED_VERSION",
            "New version must start after the currently published version's start date");
      }
      // saveAndFlush, not save - the exclusion constraint is checked per-statement and
      // Hibernate's automatic flush ordering is not guaranteed to write this close before
      // the new version's own PUBLISHED update below (the same bug fixed in
      // ProcedurePublishingService, ThresholdService, and QuestionnaireVersionService).
      if (existing.getEffectiveTo() == null || existing.getEffectiveTo().isAfter(effectiveFrom)) {
        existing.closeEffectiveTo(effectiveFrom);
        ruleVersionRepository.saveAndFlush(existing);
      }
    }

    syncThresholdReferences(version);
    version.markPublished(actor, clock.instant(), effectiveFrom);
    return version;
  }

  @Transactional
  public RuleVersion archive(UUID versionId) {
    RuleVersion version = getManagedById(versionId);
    version.archive();
    return version;
  }

  /**
   * Publish-readiness (brief §23/§27): the version must be APPROVED, have an {@code effectiveFrom},
   * a structurally and semantically valid condition tree ({@link ConditionTreeValidator} - unknown
   * fact/operator/threshold/country-group all rejected here), and at least one {@link
   * SourceRole#PRIMARY} or {@link SourceRole#LEGAL_BASIS} {@code OfficialSource} whose {@link
   * VerificationStatus} is {@code VERIFIED} - never a merely-present, {@code OUTDATED}, or {@code
   * ARCHIVED} source (brief §22).
   */
  private void validatePublishReadiness(RuleVersion version, LocalDate effectiveFrom) {
    List<ValidationIssue> issues = readiness(version, effectiveFrom);
    if (!issues.isEmpty()) {
      ValidationIssue first = issues.get(0);
      HttpStatus status =
          switch (first.code()) {
            case "MISSING_EFFECTIVE_FROM", "CONDITION_TREE_INVALID" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
          };
      throw new ApiException(status, first.code(), first.message());
    }
  }

  /**
   * Phase 9 addition (brief §42/§91) - see {@code ProcedurePublishingService#readiness}'s Javadoc.
   */
  @Transactional(readOnly = true)
  public List<ValidationIssue> readiness(UUID versionId, LocalDate effectiveFrom) {
    return readiness(getManagedById(versionId), effectiveFrom);
  }

  private List<ValidationIssue> readiness(RuleVersion version, LocalDate effectiveFrom) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (version.getStatus() != PublicationStatus.APPROVED) {
      issues.add(
          new ValidationIssue("VERSION_NOT_APPROVED", "Only an APPROVED version can be published"));
    }
    if (effectiveFrom == null) {
      issues.add(
          new ValidationIssue("MISSING_EFFECTIVE_FROM", "effectiveFrom is required to publish"));
    }
    try {
      conditionTreeValidator.validate(version.getConditionTree());
    } catch (ConditionTreeValidationException e) {
      issues.add(new ValidationIssue("CONDITION_TREE_INVALID", e.getMessage()));
    }

    List<RuleVersionSource> sources =
        ruleVersionSourceRepository.findByRuleVersion_Id(version.getId());
    boolean hasVerifiedSource =
        sources.stream()
            .anyMatch(
                s ->
                    (s.getRole() == SourceRole.PRIMARY || s.getRole() == SourceRole.LEGAL_BASIS)
                        && s.getOfficialSource().getVerificationStatus()
                            == VerificationStatus.VERIFIED);
    if (!hasVerifiedSource) {
      issues.add(
          new ValidationIssue(
              "NO_VERIFIED_SOURCE",
              "A rule version cannot be published without at least one VERIFIED primary or"
                  + " legal-basis official source"));
    }
    return issues;
  }

  /**
   * Rebuilds {@code rule_threshold_references} from the condition tree, always from scratch (brief
   * §21's "cannot drift" requirement) - never hand-maintained, never merged.
   */
  private void syncThresholdReferences(RuleVersion version) {
    ruleThresholdReferenceRepository.deleteAll(
        ruleThresholdReferenceRepository.findByRuleVersion_Id(version.getId()));

    Set<String> thresholdCodes = new LinkedHashSet<>();
    try {
      JsonNode root = objectMapper.readTree(version.getConditionTree());
      collectThresholdCodes(ConditionTreeParser.parse(root), thresholdCodes);
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONDITION_TREE_PARSE_FAILED",
          "Failed to parse condition tree while extracting threshold references: "
              + e.getMessage());
    }
    for (String thresholdCode : thresholdCodes) {
      ruleThresholdReferenceRepository.save(new RuleThresholdReference(version, thresholdCode));
    }
  }

  private static void collectThresholdCodes(ConditionNode node, Set<String> out) {
    if (node instanceof AllNode all) {
      all.children().forEach(child -> collectThresholdCodes(child, out));
    } else if (node instanceof AnyNode any) {
      any.children().forEach(child -> collectThresholdCodes(child, out));
    } else if (node instanceof NotNode not) {
      collectThresholdCodes(not.child(), out);
    } else if (node instanceof LeafCondition leaf && leaf.threshold() != null) {
      out.add(leaf.threshold());
    }
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
