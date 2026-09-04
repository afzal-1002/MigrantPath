package com.foreignerwarsaw.rules.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.observability.RuleMetrics;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.source.SourceType;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdValueType;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.reference.country.CountryClassificationService;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionSource;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The core condition-tree walker (brief §30-§36) - ALL/ANY/NOT combination semantics,
 * MISSING-short-circuit, threshold/country-group resolution, and ERROR safety, isolated with mocked
 * collaborators. {@code RuleEngineIntegrationTest} proves the same engine again end-to-end against
 * a real database.
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorTest {

  @Mock private FactResolver factResolver;
  @Mock private ThresholdService thresholdService;
  @Mock private CountryClassificationService countryClassificationService;
  @Mock private RuleVersionSourceRepository ruleVersionSourceRepository;

  // Canonical Phase 14 (Observability) - a plain Mockito mock, not a real
  // SimpleMeterRegistry-backed instance: this test class's own purpose is the
  // condition-tree evaluation semantics, not metrics (RuleMetricsIntegrationTest
  // covers those against a real MeterRegistry), so mocking this one extra
  // collaborator - consistent with every other collaborator here - keeps the test
  // focused rather than mixing concerns.
  @Mock private RuleMetrics ruleMetrics;

  private RuleEvaluator evaluator;
  private final LocalDate evaluationDate = LocalDate.of(2026, 1, 1);

  @BeforeEach
  void setUp() {
    evaluator =
        new RuleEvaluator(
            factResolver,
            thresholdService,
            countryClassificationService,
            ruleVersionSourceRepository,
            new ObjectMapper(),
            ruleMetrics);
    lenient().when(ruleVersionSourceRepository.findByRuleVersion_Id(any())).thenReturn(List.of());
  }

  private RuleVersion versionWithTree(String conditionTreeJson) {
    Rule rule =
        new Rule(
            "BLUE_CARD_ELIGIBILITY",
            "Blue Card eligibility",
            RuleType.ELIGIBILITY,
            RuleTargetType.PROCEDURE,
            "BLUE_CARD");
    ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
    RuleVersion version =
        RuleVersion.draft(rule, 1, conditionTreeJson, "rules.blueCard.eligibility", null);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    return version;
  }

  private AssessmentFacts factsWith(Map<String, Object> answers) {
    return new AssessmentFacts(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "WARSAW_GENERAL_ASSESSMENT",
        1,
        AssessmentStatus.COMPLETED,
        Instant.now(),
        evaluationDate,
        answers);
  }

  @Test
  void allNode_everyChildPasses_isSatisfied() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(true);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(true);
    RuleVersion version =
        versionWithTree(
            "{\"all\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
    assertThat(result.passedConditions()).hasSize(2);
  }

  @Test
  void allNode_anyChildFails_isNotSatisfied_evenWithAnotherMissing() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(false);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(null);
    RuleVersion version =
        versionWithTree(
            "{\"all\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    // FAIL outranks MISSING in ALL - a known "no" should never be masked by an unrelated
    // unanswered question (brief §30).
    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.NOT_SATISFIED);
  }

  @Test
  void allNode_missingWithNoFail_isIndeterminate() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(true);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(null);
    RuleVersion version =
        versionWithTree(
            "{\"all\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.INDETERMINATE);
    assertThat(result.missingFacts()).containsExactly("B");
  }

  @Test
  void anyNode_oneChildPasses_isSatisfied_evenWithAnotherFail() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(false);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(true);
    RuleVersion version =
        versionWithTree(
            "{\"any\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
  }

  @Test
  void anyNode_allFail_isNotSatisfied() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(false);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(false);
    RuleVersion version =
        versionWithTree(
            "{\"any\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.NOT_SATISFIED);
  }

  @Test
  void anyNode_missingWithNoPass_isIndeterminate() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(false);
    when(factResolver.resolve(eq("B"), any(), any())).thenReturn(null);
    RuleVersion version =
        versionWithTree(
            "{\"any\":[{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true},"
                + "{\"fact\":\"B\",\"operator\":\"EQUALS\",\"value\":true}]}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.INDETERMINATE);
  }

  @Test
  void notNode_invertsPassAndFail() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(true);
    RuleVersion version =
        versionWithTree("{\"not\":{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true}}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.NOT_SATISFIED);
  }

  @Test
  void notNode_passesThroughMissingUnchanged() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(null);
    RuleVersion version =
        versionWithTree("{\"not\":{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true}}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    // Negating "I don't know yet" can never manufacture a known answer (brief §31).
    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.INDETERMINATE);
  }

  @Test
  void leaf_resolvesAThresholdReferenceAndRecordsUsage() {
    when(factResolver.resolve(eq("SALARY_MONTHLY_GROSS"), any(), any()))
        .thenReturn(new BigDecimal("16000"));
    Threshold threshold =
        new Threshold("BLUE_CARD_SALARY_THRESHOLD", "Blue Card salary", ThresholdValueType.MONEY);
    ThresholdVersion thresholdVersion =
        ThresholdVersion.draft(threshold, new BigDecimal("15000"), null, null);
    ReflectionTestUtils.setField(thresholdVersion, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(thresholdVersion, "effectiveFrom", LocalDate.of(2025, 1, 1));
    when(thresholdService.findActiveVersion("BLUE_CARD_SALARY_THRESHOLD", evaluationDate))
        .thenReturn(Optional.of(thresholdVersion));
    RuleVersion version =
        versionWithTree(
            "{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN_OR_EQUAL\",\"threshold\":\"BLUE_CARD_SALARY_THRESHOLD\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
    assertThat(result.thresholdsUsed()).hasSize(1);
    assertThat(result.thresholdsUsed().get(0).thresholdCode())
        .isEqualTo("BLUE_CARD_SALARY_THRESHOLD");
    assertThat(result.thresholdsUsed().get(0).value()).isEqualByComparingTo("15000");
  }

  /**
   * Canonical Phase 14 (Observability) - the "rule evaluation error" failure exercise: a real
   * unresolvable threshold reference (no active published {@code ThresholdVersion}) must produce
   * both an {@code ERROR}-status result AND the correct observability signal - {@code
   * ruleMetrics.recordEvaluation(ERROR)} plus {@code recordError(THRESHOLD_RESOLUTION)}, never left
   * unverified as a side effect nobody actually checks.
   */
  @Test
  void leaf_noActivePublishedThresholdVersion_isError_neverSilentlyPass() {
    when(factResolver.resolve(eq("SALARY_MONTHLY_GROSS"), any(), any()))
        .thenReturn(new BigDecimal("16000"));
    when(thresholdService.findActiveVersion(anyString(), any())).thenReturn(Optional.empty());
    RuleVersion version =
        versionWithTree(
            "{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN_OR_EQUAL\",\"threshold\":\"BLUE_CARD_SALARY_THRESHOLD\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.ERROR);
    assertThat(result.errorConditions()).hasSize(1);
    verify(ruleMetrics).recordEvaluation(RuleEvaluationStatus.ERROR);
    verify(ruleMetrics).recordError(RuleMetrics.ErrorCategory.THRESHOLD_RESOLUTION);
  }

  @Test
  void leaf_isMemberOfCountryGroup_delegatesToCountryClassificationService() {
    when(factResolver.resolve(eq("CITIZENSHIP_COUNTRY"), any(), any())).thenReturn("DE");
    when(countryClassificationService.isMember("DE", "EU_MEMBER", evaluationDate)).thenReturn(true);
    RuleVersion version =
        versionWithTree(
            "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"IS_MEMBER_OF_COUNTRY_GROUP\",\"value\":\"EU_MEMBER\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
  }

  @Test
  void leaf_isNotMemberOfCountryGroup_invertsMembership() {
    when(factResolver.resolve(eq("CITIZENSHIP_COUNTRY"), any(), any())).thenReturn("PK");
    when(countryClassificationService.isMember("PK", "EU_MEMBER", evaluationDate))
        .thenReturn(false);
    RuleVersion version =
        versionWithTree(
            "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"IS_NOT_MEMBER_OF_COUNTRY_GROUP\",\"value\":\"EU_MEMBER\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
  }

  @Test
  void leaf_missingFact_recordedAsMissingConditionAndMissingFact_neverAsFail() {
    when(factResolver.resolve(eq("DATE_OF_BIRTH"), any(), any())).thenReturn(null);
    RuleVersion version =
        versionWithTree(
            "{\"fact\":\"DATE_OF_BIRTH\",\"operator\":\"DATE_BEFORE\",\"value\":\"2008-01-01\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.INDETERMINATE);
    assertThat(result.failedConditions()).isEmpty();
    assertThat(result.missingConditions()).hasSize(1);
    assertThat(result.missingFacts()).containsExactly("DATE_OF_BIRTH");
  }

  @Test
  void leaf_existsOperatorNeverTreatedAsMissing() {
    when(factResolver.resolve(eq("X"), any(), any())).thenReturn(null);
    RuleVersion version = versionWithTree("{\"fact\":\"X\",\"operator\":\"NOT_EXISTS\"}");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    // NOT_EXISTS legitimately evaluates a null fact - it must resolve to PASS/FAIL, never MISSING.
    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
    assertThat(result.missingConditions()).isEmpty();
  }

  /**
   * Canonical Phase 14 (Observability) - a second "rule evaluation error" failure exercise: a
   * genuinely malformed condition tree (the CONFIGURATION category) must also produce both the
   * correct result status and the correct metric signal, distinct from the THRESHOLD_RESOLUTION
   * case above.
   */
  @Test
  void malformedConditionTreeJson_producesErrorStatus_neverThrows() {
    RuleVersion version = versionWithTree("{not valid json");

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.ERROR);
    assertThat(result.errorConditions()).hasSize(1);
    verify(ruleMetrics).recordEvaluation(RuleEvaluationStatus.ERROR);
    verify(ruleMetrics).recordError(RuleMetrics.ErrorCategory.CONFIGURATION);
  }

  @Test
  void sourceIds_populatedFromRuleVersionSourceRepository() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(true);
    RuleVersion version =
        versionWithTree("{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true}");
    OfficialSource source =
        OfficialSource.draft(
            "Ustawa o cudzoziemcach", "https://isap.sejm.gov.pl", SourceType.LEGISLATION);
    ReflectionTestUtils.setField(source, "id", UUID.randomUUID());
    RuleVersionSource association = new RuleVersionSource(version, source, SourceRole.LEGAL_BASIS);
    when(ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()))
        .thenReturn(List.of(association));

    RuleEvaluationResult result = evaluator.evaluate(version, factsWith(Map.of()), evaluationDate);

    assertThat(result.sourceIds()).containsExactly(source.getId());
  }

  @Test
  void previewEvaluate_carriesNoRuleIdentity() {
    when(factResolver.resolve(eq("A"), any(), any())).thenReturn(true);

    RuleEvaluationResult result =
        evaluator.previewEvaluate(
            "{\"fact\":\"A\",\"operator\":\"EQUALS\",\"value\":true}",
            "rules.preview",
            factsWith(Map.of()),
            evaluationDate);

    assertThat(result.ruleId()).isNull();
    assertThat(result.ruleVersionId()).isNull();
    assertThat(result.ruleCode()).isEqualTo("PREVIEW");
    assertThat(result.status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
    assertThat(result.sourceIds()).isEmpty();
  }

  /**
   * Canonical Phase 11 brief §27 - real production rule regression, at this layer specifically
   * because {@code MELDUNEK_BASE_APPLICABILITY}'s fact ({@code PRIMARY_PURPOSE CONTAINS
   * "GET_MELDUNEK"}) can only be produced through the real assessment API by a {@code
   * QuestionnaireVersion} 2 that exists solely as live data created via the real Admin workflow in
   * the dev database (docs/legal-content/PRODUCTION_RULE_COVERAGE.md's "QuestionnaireVersion 2"
   * section) - a fresh Testcontainers database (only V38's original v1) genuinely rejects {@code
   * GET_MELDUNEK} as "not a valid option," confirmed when this test was first attempted as a
   * full-HTTP-flow integration test in {@code ProductionRuleRegressionTest} and moved here instead.
   * The condition tree below is copied verbatim from the same real database query documented in
   * that class - see it for the other five real rules' full-HTTP-flow regression coverage.
   */
  @Test
  void meldunekBaseApplicability_realProductionConditionTree_passOnGoalSelected_failOtherwise() {
    String realConditionTree =
        "{\"code\":\"MELDUNEK_GOAL_SELECTED\",\"fact\":\"PRIMARY_PURPOSE\",\"value\":\"GET_MELDUNEK\",\"operator\":\"CONTAINS\",\"explanationKey\":\"meldunek.applicability.goalSelected\"}";
    RuleVersion version = versionWithTree(realConditionTree);

    when(factResolver.resolve(eq("PRIMARY_PURPOSE"), any(), any()))
        .thenReturn(List.of("GET_MELDUNEK"));
    assertThat(evaluator.evaluate(version, factsWith(Map.of()), evaluationDate).status())
        .isEqualTo(RuleEvaluationStatus.SATISFIED);

    when(factResolver.resolve(eq("PRIMARY_PURPOSE"), any(), any()))
        .thenReturn(List.of("GET_PESEL"));
    assertThat(evaluator.evaluate(version, factsWith(Map.of()), evaluationDate).status())
        .isEqualTo(RuleEvaluationStatus.NOT_SATISFIED);
  }
}
