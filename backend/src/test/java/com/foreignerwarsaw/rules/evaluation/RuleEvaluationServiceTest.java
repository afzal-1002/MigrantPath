package com.foreignerwarsaw.rules.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RuleRepository;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The assessment-facing entry point (brief §37-§39) - target lookup, the Active-Version Predicate
 * per rule, and result grouping, isolated with a mocked {@link RuleEvaluator}.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluationServiceTest {

  @Mock private RuleRepository ruleRepository;
  @Mock private RuleVersionRepository ruleVersionRepository;
  @Mock private RuleEvaluator ruleEvaluator;

  private RuleEvaluationService service;
  private final LocalDate evaluationDate = LocalDate.of(2026, 1, 1);

  @BeforeEach
  void setUp() {
    service = new RuleEvaluationService(ruleRepository, ruleVersionRepository, ruleEvaluator);
  }

  private Rule rule(String code, RuleTargetType targetType, String targetCode) {
    Rule rule = new Rule(code, code, RuleType.ELIGIBILITY, targetType, targetCode);
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    return rule;
  }

  private RuleVersion publishedVersionOf(Rule rule) {
    RuleVersion version =
        RuleVersion.draft(rule, 1, "{\"fact\":\"A\",\"operator\":\"EXISTS\"}", "k", null);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    return version;
  }

  private RuleEvaluationResult resultFor(Rule rule, RuleVersion version) {
    return new RuleEvaluationResult(
        rule.getId(),
        rule.getCode(),
        rule.getRuleType(),
        version.getId(),
        1,
        rule.getTargetType(),
        rule.getTargetCode(),
        evaluationDate,
        RuleEvaluationStatus.SATISFIED,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Set.of(),
        List.of(),
        List.of(),
        "k");
  }

  private AssessmentFacts facts() {
    return new AssessmentFacts(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "WARSAW_GENERAL_ASSESSMENT",
        1,
        AssessmentStatus.COMPLETED,
        Instant.now(),
        evaluationDate,
        Map.of());
  }

  @Test
  void evaluateRulesForProcedure_onlyEvaluatesActiveRulesTargetingThatProcedure() {
    Rule rule = rule("BLUE_CARD_ELIGIBILITY", RuleTargetType.PROCEDURE, "BLUE_CARD");
    RuleVersion version = publishedVersionOf(rule);
    when(ruleRepository.findByTargetTypeAndTargetCodeIgnoreCaseAndActiveTrue(
            RuleTargetType.PROCEDURE, "BLUE_CARD"))
        .thenReturn(List.of(rule));
    when(ruleVersionRepository.findActivePublishedVersion(rule.getId(), evaluationDate))
        .thenReturn(Optional.of(version));
    when(ruleEvaluator.evaluate(eq(version), any(), any())).thenReturn(resultFor(rule, version));

    List<RuleEvaluationResult> results =
        service.evaluateRulesForProcedure("BLUE_CARD", facts(), evaluationDate);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).ruleCode()).isEqualTo("BLUE_CARD_ELIGIBILITY");
  }

  @Test
  void evaluateRulesForProcedure_skipsARuleWithNoActivePublishedVersion() {
    Rule rule = rule("BLUE_CARD_ELIGIBILITY", RuleTargetType.PROCEDURE, "BLUE_CARD");
    when(ruleRepository.findByTargetTypeAndTargetCodeIgnoreCaseAndActiveTrue(
            RuleTargetType.PROCEDURE, "BLUE_CARD"))
        .thenReturn(List.of(rule));
    when(ruleVersionRepository.findActivePublishedVersion(rule.getId(), evaluationDate))
        .thenReturn(Optional.empty());

    List<RuleEvaluationResult> results =
        service.evaluateRulesForProcedure("BLUE_CARD", facts(), evaluationDate);

    // A content gap ("nothing published yet") is silently absent, never surfaced as an error
    // or a MISSING/ERROR result about the user's facts.
    assertThat(results).isEmpty();
  }

  @Test
  void evaluateApplicableRules_groupsResultsByTargetCode() {
    Rule blueCardRule = rule("BLUE_CARD_ELIGIBILITY", RuleTargetType.PROCEDURE, "BLUE_CARD");
    Rule permanentResidenceRule =
        rule("PR_ELIGIBILITY", RuleTargetType.PROCEDURE, "PERMANENT_RESIDENCE");
    RuleVersion blueCardVersion = publishedVersionOf(blueCardRule);
    RuleVersion prVersion = publishedVersionOf(permanentResidenceRule);
    when(ruleRepository.findByActiveTrue())
        .thenReturn(List.of(blueCardRule, permanentResidenceRule));
    when(ruleVersionRepository.findActivePublishedVersion(blueCardRule.getId(), evaluationDate))
        .thenReturn(Optional.of(blueCardVersion));
    when(ruleVersionRepository.findActivePublishedVersion(
            permanentResidenceRule.getId(), evaluationDate))
        .thenReturn(Optional.of(prVersion));
    when(ruleEvaluator.evaluate(eq(blueCardVersion), any(), any()))
        .thenReturn(resultFor(blueCardRule, blueCardVersion));
    when(ruleEvaluator.evaluate(eq(prVersion), any(), any()))
        .thenReturn(resultFor(permanentResidenceRule, prVersion));

    RuleEvaluationBundle bundle = service.evaluateApplicableRules(facts(), evaluationDate);

    assertThat(bundle.resultsByTargetCode()).containsOnlyKeys("BLUE_CARD", "PERMANENT_RESIDENCE");
    assertThat(bundle.engineVersion()).isEqualTo(RuleEvaluator.ENGINE_VERSION);
  }

  @Test
  void evaluateApplicableRules_unionsMissingFactsAcrossAllResults() {
    Rule rule = rule("BLUE_CARD_ELIGIBILITY", RuleTargetType.PROCEDURE, "BLUE_CARD");
    RuleVersion version = publishedVersionOf(rule);
    when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
    when(ruleVersionRepository.findActivePublishedVersion(rule.getId(), evaluationDate))
        .thenReturn(Optional.of(version));
    RuleEvaluationResult indeterminate =
        new RuleEvaluationResult(
            rule.getId(),
            rule.getCode(),
            rule.getRuleType(),
            version.getId(),
            1,
            rule.getTargetType(),
            rule.getTargetCode(),
            evaluationDate,
            RuleEvaluationStatus.INDETERMINATE,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Set.of("SALARY_MONTHLY_GROSS"),
            List.of(),
            List.of(),
            "k");
    when(ruleEvaluator.evaluate(eq(version), any(), any())).thenReturn(indeterminate);

    RuleEvaluationBundle bundle = service.evaluateApplicableRules(facts(), evaluationDate);

    assertThat(bundle.missingFacts()).containsExactly("SALARY_MONTHLY_GROSS");
  }

  @Test
  void previewEvaluate_delegatesStraightToTheEvaluator() {
    RuleEvaluationResult preview =
        new RuleEvaluationResult(
            null,
            "PREVIEW",
            null,
            null,
            0,
            null,
            null,
            evaluationDate,
            RuleEvaluationStatus.SATISFIED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Set.of(),
            List.of(),
            List.of(),
            "k");
    when(ruleEvaluator.previewEvaluate("{}", "k", null, evaluationDate)).thenReturn(preview);

    RuleEvaluationResult result = service.previewEvaluate("{}", "k", null, evaluationDate);

    assertThat(result).isSameAs(preview);
  }
}
