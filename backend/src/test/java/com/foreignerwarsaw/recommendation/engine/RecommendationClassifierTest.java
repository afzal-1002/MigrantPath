package com.foreignerwarsaw.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Required/exclusion/informational aggregation semantics (Recommendation Engine brief §17/§18,
 * docs/recommendations/RECOMMENDATION_POLICY.md) - every scenario the brief's §93-§114 test list
 * names, expressed directly against {@link RuleEvaluationResult}s rather than a real database.
 */
class RecommendationClassifierTest {

  private final RecommendationClassifier classifier = new RecommendationClassifier();

  private RuleEvaluationResult result(RuleType ruleType, RuleEvaluationStatus status) {
    return new RuleEvaluationResult(
        UUID.randomUUID(),
        ruleType + "_RULE",
        ruleType,
        UUID.randomUUID(),
        1,
        RuleTargetType.PROCEDURE,
        "TEST_PROCEDURE",
        LocalDate.of(2026, 1, 1),
        status,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Set.of(),
        List.of(),
        List.of(),
        "k");
  }

  @Test
  void allRequiredSatisfied_noExclusion_isPrimaryMatchCandidate() {
    var results = List.of(result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.PRIMARY_MATCH);
  }

  @Test
  void aFailedRequiredRule_isNotApplicable_regardlessOfOtherMissingInformation() {
    // Brief §96/§17: job offer FALSE outranks salary MISSING - a known "no" is definitive.
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.NOT_SATISFIED),
            result(RuleType.REQUIREMENT, RuleEvaluationStatus.INDETERMINATE));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.NOT_APPLICABLE);
  }

  @Test
  void aMissingRequiredRule_withNoFailure_isMoreInformationRequired() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.REQUIREMENT, RuleEvaluationStatus.INDETERMINATE));
    assertThat(classifier.classify(results))
        .isEqualTo(RecommendationType.MORE_INFORMATION_REQUIRED);
  }

  @Test
  void aSatisfiedExclusion_isNotApplicable_evenWhenRequiredRulesPass() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.EXCLUSION, RuleEvaluationStatus.SATISFIED));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.NOT_APPLICABLE);
  }

  @Test
  void anIndeterminateExclusion_isMoreInformationRequired_neverConfidentlyRecommended() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.EXCLUSION, RuleEvaluationStatus.INDETERMINATE));
    assertThat(classifier.classify(results))
        .isEqualTo(RecommendationType.MORE_INFORMATION_REQUIRED);
  }

  @Test
  void aNotSatisfiedExclusion_neverBlocksAMatch() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.EXCLUSION, RuleEvaluationStatus.NOT_SATISFIED));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.PRIMARY_MATCH);
  }

  @Test
  void anyErrorAnywhere_isUnavailableForAnalysis_neverNotApplicableNorAConfidentMatch() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.REQUIREMENT, RuleEvaluationStatus.ERROR));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.UNAVAILABLE_FOR_ANALYSIS);
  }

  @Test
  void anErrorEvenWithAFailedRequiredRule_stillUnavailableForAnalysis_notNotApplicable() {
    // Brief §48: ERROR must never be silently converted into a confident NOT_APPLICABLE.
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.NOT_SATISFIED),
            result(RuleType.REQUIREMENT, RuleEvaluationStatus.ERROR));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.UNAVAILABLE_FOR_ANALYSIS);
  }

  @Test
  void informationalRuleResults_neverGateTheClassification() {
    var results =
        List.of(
            result(RuleType.ELIGIBILITY, RuleEvaluationStatus.SATISFIED),
            result(RuleType.INFORMATION_REQUIRED, RuleEvaluationStatus.NOT_SATISFIED),
            result(RuleType.INFORMATION_REQUIRED, RuleEvaluationStatus.INDETERMINATE));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.PRIMARY_MATCH);
  }

  @Test
  void noRequiredOrExclusionRulesAtAll_defaultsToPrimaryMatchCandidate() {
    var results = List.of(result(RuleType.INFORMATION_REQUIRED, RuleEvaluationStatus.SATISFIED));
    assertThat(classifier.classify(results)).isEqualTo(RecommendationType.PRIMARY_MATCH);
  }
}
