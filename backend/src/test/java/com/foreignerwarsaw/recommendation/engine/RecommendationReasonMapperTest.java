package com.foreignerwarsaw.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.recommendation.core.RecommendationReasonType;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.evaluation.ConditionResult;
import com.foreignerwarsaw.rules.evaluation.ConditionTrace;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Phase 6 condition traces -> stable, structured reason codes (brief §11/§12/§76) - never a raw
 * technical trace exposed as-is.
 */
class RecommendationReasonMapperTest {

  private final RecommendationReasonMapper mapper = new RecommendationReasonMapper();

  private ConditionTrace trace(String code, ConditionResult result, String fact) {
    return new ConditionTrace(
        code, "root." + code, fact, ComparisonOperator.EQUALS, result, "k." + code, "msg");
  }

  private RuleEvaluationResult result(
      String ruleCode,
      RuleType ruleType,
      RuleEvaluationStatus status,
      List<ConditionTrace> passed,
      List<ConditionTrace> failed,
      List<ConditionTrace> missing,
      List<ConditionTrace> errors) {
    return new RuleEvaluationResult(
        UUID.randomUUID(),
        ruleCode,
        ruleType,
        UUID.randomUUID(),
        1,
        RuleTargetType.PROCEDURE,
        "TEST_PROCEDURE",
        LocalDate.of(2026, 1, 1),
        status,
        passed,
        failed,
        missing,
        errors,
        Set.of(),
        List.of(),
        List.of(),
        "rule.explanation");
  }

  @Test
  void requiredRule_passedAndFailedConditionsBecomeMatchedAndFailedReasons() {
    var r =
        result(
            "ELIGIBILITY_RULE",
            RuleType.ELIGIBILITY,
            RuleEvaluationStatus.NOT_SATISFIED,
            List.of(trace("GOAL_MATCH", ConditionResult.PASS, "GOALS")),
            List.of(trace("SALARY_CHECK", ConditionResult.FAIL, "MONTHLY_GROSS_SALARY")),
            List.of(),
            List.of());

    var drafts = mapper.mapReasons(List.of(r));

    assertThat(drafts).hasSize(2);
    assertThat(drafts)
        .anySatisfy(
            d -> {
              assertThat(d.type()).isEqualTo(RecommendationReasonType.MATCHED_CONDITION);
              assertThat(d.conditionCode()).isEqualTo("GOAL_MATCH");
            });
    assertThat(drafts)
        .anySatisfy(
            d -> {
              assertThat(d.type()).isEqualTo(RecommendationReasonType.FAILED_CONDITION);
              assertThat(d.conditionCode()).isEqualTo("SALARY_CHECK");
              assertThat(d.factCode()).isEqualTo("MONTHLY_GROSS_SALARY");
            });
  }

  @Test
  void requiredRule_missingConditionsBecomeMissingInformationReasons() {
    var r =
        result(
            "REQUIREMENT_RULE",
            RuleType.REQUIREMENT,
            RuleEvaluationStatus.INDETERMINATE,
            List.of(),
            List.of(),
            List.of(trace("SALARY_KNOWN", ConditionResult.MISSING, "MONTHLY_GROSS_SALARY")),
            List.of());

    var drafts = mapper.mapReasons(List.of(r));

    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).type()).isEqualTo(RecommendationReasonType.MISSING_INFORMATION);
    assertThat(drafts.get(0).factCode()).isEqualTo("MONTHLY_GROSS_SALARY");
  }

  @Test
  void satisfiedExclusion_passedConditionsBecomeExclusionReasons_neverMatchedCondition() {
    var r =
        result(
            "EXCLUSION_RULE",
            RuleType.EXCLUSION,
            RuleEvaluationStatus.SATISFIED,
            List.of(trace("IS_EU_CITIZEN", ConditionResult.PASS, "CITIZENSHIP_COUNTRY")),
            List.of(),
            List.of(),
            List.of());

    var drafts = mapper.mapReasons(List.of(r));

    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).type()).isEqualTo(RecommendationReasonType.EXCLUSION);
  }

  @Test
  void notSatisfiedExclusion_producesNoReasons_notInterestingToAUser() {
    var r =
        result(
            "EXCLUSION_RULE",
            RuleType.EXCLUSION,
            RuleEvaluationStatus.NOT_SATISFIED,
            List.of(),
            List.of(trace("IS_EU_CITIZEN", ConditionResult.FAIL, "CITIZENSHIP_COUNTRY")),
            List.of(),
            List.of());

    assertThat(mapper.mapReasons(List.of(r))).isEmpty();
  }

  @Test
  void indeterminateExclusion_missingConditionsBecomeMissingInformation() {
    var r =
        result(
            "EXCLUSION_RULE",
            RuleType.EXCLUSION,
            RuleEvaluationStatus.INDETERMINATE,
            List.of(),
            List.of(),
            List.of(trace("IS_EU_CITIZEN", ConditionResult.MISSING, "CITIZENSHIP_COUNTRY")),
            List.of());

    var drafts = mapper.mapReasons(List.of(r));

    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).type()).isEqualTo(RecommendationReasonType.MISSING_INFORMATION);
  }

  @Test
  void errorStatus_producesExactlyOneAnalysisErrorReason_neverExposesRawException() {
    var r =
        result(
            "BROKEN_RULE",
            RuleType.ELIGIBILITY,
            RuleEvaluationStatus.ERROR,
            List.of(),
            List.of(),
            List.of(),
            List.of(trace("root", ConditionResult.ERROR, null)));

    var drafts = mapper.mapReasons(List.of(r));

    assertThat(drafts).hasSize(1);
    assertThat(drafts.get(0).type()).isEqualTo(RecommendationReasonType.ANALYSIS_ERROR);
    assertThat(drafts.get(0).conditionCode()).isNull();
  }

  @Test
  void ordering_isDeterministicByRuleTypeThenRuleCode_regardlessOfInputOrder() {
    var exclusionRule =
        result(
            "Z_EXCLUSION",
            RuleType.EXCLUSION,
            RuleEvaluationStatus.SATISFIED,
            List.of(trace("EXCLUDED", ConditionResult.PASS, "X")),
            List.of(),
            List.of(),
            List.of());
    var eligibilityRule =
        result(
            "A_ELIGIBILITY",
            RuleType.ELIGIBILITY,
            RuleEvaluationStatus.SATISFIED,
            List.of(trace("MATCHED", ConditionResult.PASS, "Y")),
            List.of(),
            List.of(),
            List.of());

    var drafts = mapper.mapReasons(List.of(exclusionRule, eligibilityRule));

    // ELIGIBILITY sorts before EXCLUSION alphabetically - proving rule input order is
    // never relied upon.
    assertThat(drafts).extracting(d -> d.conditionCode()).containsExactly("MATCHED", "EXCLUDED");
  }
}
