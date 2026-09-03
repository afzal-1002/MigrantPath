package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic classification of one Procedure's {@link RuleEvaluationResult}s into a {@link
 * RecommendationType} (Recommendation Engine brief §17/§18/§48 - full policy table in
 * docs/recommendations/RECOMMENDATION_POLICY.md). Reuses Phase 6's {@link RuleType} directly as the
 * "rule role" concept the brief asks for (brief §16) rather than introducing a parallel field:
 * {@code ELIGIBILITY}/{@code APPLICABILITY}/{@code REQUIREMENT} rules are "required," {@code
 * EXCLUSION} rules are exclusionary, {@code INFORMATION_REQUIRED} rules are purely informational
 * and never gate the outcome on their own.
 *
 * <p>Pure and stateless - the same input list always produces the same {@link RecommendationType}
 * (brief §74). Never returns {@link RecommendationType#POSSIBLE_ALTERNATIVE} - distinguishing a
 * top-tier match from a secondary one is {@code RecommendationRanker}'s job, not this class's
 * (brief §5's "ranking logic places it ahead" - a ranking concern, not a per-procedure rule-result
 * concern).
 */
@Component
public class RecommendationClassifier {

  private static final Set<RuleType> REQUIRED_TYPES =
      Set.of(RuleType.ELIGIBILITY, RuleType.APPLICABILITY, RuleType.REQUIREMENT);

  public RecommendationType classify(List<RuleEvaluationResult> results) {
    if (results.stream().anyMatch(r -> r.status() == RuleEvaluationStatus.ERROR)) {
      // Brief §48/§64: an ERROR anywhere for this procedure means the engine cannot
      // confidently say anything about it - never silently folded into NOT_SATISFIED
      // or any other confident category.
      return RecommendationType.UNAVAILABLE_FOR_ANALYSIS;
    }

    List<RuleEvaluationResult> exclusions = byType(results, RuleType.EXCLUSION);
    if (exclusions.stream().anyMatch(r -> r.status() == RuleEvaluationStatus.SATISFIED)) {
      // Brief §18: a SATISFIED exclusion rule always wins, regardless of required-rule
      // state - an applicable exclusion is a definitive "does not apply."
      return RecommendationType.NOT_APPLICABLE;
    }

    List<RuleEvaluationResult> required = byType(results, REQUIRED_TYPES);
    if (required.stream().anyMatch(r -> r.status() == RuleEvaluationStatus.NOT_SATISFIED)) {
      // Brief §17: a known "no" on a required rule is definitive, never demoted to
      // "more information required" just because something else is also unknown.
      return RecommendationType.NOT_APPLICABLE;
    }

    boolean anyRequiredIndeterminate =
        required.stream().anyMatch(r -> r.status() == RuleEvaluationStatus.INDETERMINATE);
    boolean anyExclusionIndeterminate =
        exclusions.stream().anyMatch(r -> r.status() == RuleEvaluationStatus.INDETERMINATE);
    if (anyRequiredIndeterminate || anyExclusionIndeterminate) {
      // Brief §17/§18/§113: an unknown required fact, or an exclusion whose own
      // applicability isn't yet known, must not be recommended confidently either way.
      return RecommendationType.MORE_INFORMATION_REQUIRED;
    }

    // Every required rule SATISFIED (or none exist), no exclusion applies or is
    // uncertain - a genuine match candidate. RecommendationRanker decides whether this
    // stays PRIMARY_MATCH or is demoted to POSSIBLE_ALTERNATIVE.
    return RecommendationType.PRIMARY_MATCH;
  }

  private List<RuleEvaluationResult> byType(List<RuleEvaluationResult> results, RuleType type) {
    return results.stream().filter(r -> r.ruleType() == type).toList();
  }

  private List<RuleEvaluationResult> byType(
      List<RuleEvaluationResult> results, Set<RuleType> types) {
    return results.stream().filter(r -> types.contains(r.ruleType())).toList();
  }
}
