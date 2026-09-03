package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RuleRepository;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The assessment-facing entry point into the rules engine (brief §37-§39) - looks up which {@link
 * Rule}s apply, resolves each one's currently-{@code PUBLISHED} {@code RuleVersion} for the
 * evaluation date via the Active-Version Predicate, and delegates the actual condition-tree walking
 * to {@link RuleEvaluator}. Deliberately produces only {@link RuleEvaluationResult}/{@link
 * RuleEvaluationBundle} - no ranking, no {@code PRIMARY_MATCH}/{@code POSSIBLE_ALTERNATIVE}/{@code
 * MORE_INFORMATION_REQUIRED} (brief §79/§90, Phase 7's job).
 *
 * <p>A rule with no currently-{@code PUBLISHED} version on {@code evaluationDate} is silently
 * skipped, never surfaced as {@code ERROR}/{@code MISSING} - "no rule content exists yet for this"
 * is a content-authoring gap, not a fact gap about the user, and conflating the two would make an
 * incomplete rule catalogue look like an indeterminate assessment.
 */
@Service
public class RuleEvaluationService {

  private final RuleRepository ruleRepository;
  private final RuleVersionRepository ruleVersionRepository;
  private final RuleEvaluator ruleEvaluator;

  public RuleEvaluationService(
      RuleRepository ruleRepository,
      RuleVersionRepository ruleVersionRepository,
      RuleEvaluator ruleEvaluator) {
    this.ruleRepository = ruleRepository;
    this.ruleVersionRepository = ruleVersionRepository;
    this.ruleEvaluator = ruleEvaluator;
  }

  /**
   * Every active rule targeting one procedure (brief §38), evaluated against one assessment's
   * facts.
   */
  @Transactional(readOnly = true)
  public List<RuleEvaluationResult> evaluateRulesForProcedure(
      String procedureCode, AssessmentFacts facts, LocalDate evaluationDate) {
    List<Rule> rules =
        ruleRepository.findByTargetTypeAndTargetCodeIgnoreCaseAndActiveTrue(
            RuleTargetType.PROCEDURE, procedureCode);
    return evaluateAll(rules, facts, evaluationDate);
  }

  /**
   * Every active rule regardless of target (brief §39/§78), grouped by {@code targetCode} so a
   * caller can aggregate per-target without re-grouping itself.
   */
  @Transactional(readOnly = true)
  public RuleEvaluationBundle evaluateApplicableRules(
      AssessmentFacts facts, LocalDate evaluationDate) {
    List<Rule> rules = ruleRepository.findByActiveTrue();
    List<RuleEvaluationResult> results = evaluateAll(rules, facts, evaluationDate);

    Map<String, List<RuleEvaluationResult>> resultsByTargetCode =
        results.stream()
            .collect(
                Collectors.groupingBy(
                    RuleEvaluationResult::targetCode, LinkedHashMap::new, Collectors.toList()));
    Set<String> missingFacts =
        results.stream()
            .flatMap(result -> result.missingFacts().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new RuleEvaluationBundle(
        facts.assessmentId(),
        evaluationDate,
        resultsByTargetCode,
        missingFacts,
        RuleEvaluator.ENGINE_VERSION);
  }

  /**
   * A dry-run against a not-yet-persisted condition tree (brief §113) - delegates straight to
   * {@link RuleEvaluator#previewEvaluate}, no {@link Rule}/{@link
   * com.foreignerwarsaw.rules.core.RuleVersion} lookup involved.
   */
  @Transactional(readOnly = true)
  public RuleEvaluationResult previewEvaluate(
      String conditionTreeJson,
      String explanationKey,
      AssessmentFacts facts,
      LocalDate evaluationDate) {
    return ruleEvaluator.previewEvaluate(conditionTreeJson, explanationKey, facts, evaluationDate);
  }

  private List<RuleEvaluationResult> evaluateAll(
      List<Rule> rules, AssessmentFacts facts, LocalDate evaluationDate) {
    List<RuleEvaluationResult> results = new ArrayList<>();
    for (Rule rule : rules) {
      ruleVersionRepository
          .findActivePublishedVersion(rule.getId(), evaluationDate)
          .ifPresent(
              version -> results.add(ruleEvaluator.evaluate(version, facts, evaluationDate)));
    }
    return results;
  }
}
