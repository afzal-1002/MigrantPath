package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.recommendation.core.RecommendationReasonType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.evaluation.ConditionTrace;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Translates Phase 6 {@link ConditionTrace}s into stable, structured {@link ReasonDraft}s (brief
 * §11/§12/§76) - a normal user never sees a raw condition path or exception message, only these
 * codes, which the frontend maps to translated copy via {@link ReasonDraft#messageKey()}. Raw
 * traces remain available on the underlying {@link RuleEvaluationResult} for debugging/admin/audit
 * (brief §12), this class just never re-exposes them as-is.
 *
 * <p>Deterministic ordering (brief §32's "preserve deterministic ordering"): rules are processed
 * sorted by {@code (ruleType, ruleCode)}, and within one rule, conditions in the order Phase 6
 * itself already returns them (passed, then failed, then missing) - never database/collection
 * iteration order.
 */
@Component
public class RecommendationReasonMapper {

  private static final Comparator<RuleEvaluationResult> STABLE_ORDER =
      Comparator.<RuleEvaluationResult, RuleType>comparing(RuleEvaluationResult::ruleType)
          .thenComparing(RuleEvaluationResult::ruleCode);

  public List<ReasonDraft> mapReasons(List<RuleEvaluationResult> results) {
    List<ReasonDraft> drafts = new ArrayList<>();
    results.stream().sorted(STABLE_ORDER).forEach(result -> appendReasonsForRule(result, drafts));
    return drafts;
  }

  private void appendReasonsForRule(RuleEvaluationResult result, List<ReasonDraft> drafts) {
    if (result.status() == RuleEvaluationStatus.ERROR) {
      drafts.add(
          new ReasonDraft(
              RecommendationReasonType.ANALYSIS_ERROR,
              result.ruleCode() + "_ANALYSIS_ERROR",
              result.ruleVersionId(),
              null,
              null,
              "recommendation.analysisError"));
      return;
    }

    if (result.ruleType() == RuleType.EXCLUSION) {
      // A SATISFIED exclusion's passed conditions are what triggered the exclusion -
      // reason type EXCLUSION, never MATCHED_CONDITION (brief §7). An INDETERMINATE
      // exclusion's missing conditions are why the whole procedure is
      // MORE_INFORMATION_REQUIRED. A NOT_SATISFIED exclusion (it simply doesn't apply)
      // produces no reasons - not interesting to a user.
      if (result.status() == RuleEvaluationStatus.SATISFIED) {
        for (ConditionTrace trace : result.passedConditions()) {
          drafts.add(toDraft(RecommendationReasonType.EXCLUSION, trace, result));
        }
      } else if (result.status() == RuleEvaluationStatus.INDETERMINATE) {
        for (ConditionTrace trace : result.missingConditions()) {
          drafts.add(toDraft(RecommendationReasonType.MISSING_INFORMATION, trace, result));
        }
      }
      return;
    }

    for (ConditionTrace trace : result.passedConditions()) {
      drafts.add(toDraft(RecommendationReasonType.MATCHED_CONDITION, trace, result));
    }
    for (ConditionTrace trace : result.failedConditions()) {
      drafts.add(toDraft(RecommendationReasonType.FAILED_CONDITION, trace, result));
    }
    for (ConditionTrace trace : result.missingConditions()) {
      drafts.add(toDraft(RecommendationReasonType.MISSING_INFORMATION, trace, result));
    }
  }

  private ReasonDraft toDraft(
      RecommendationReasonType type, ConditionTrace trace, RuleEvaluationResult result) {
    String messageKey =
        trace.explanationKey() != null ? trace.explanationKey() : result.explanationKey();
    return new ReasonDraft(
        type,
        trace.conditionCode(),
        result.ruleVersionId(),
        trace.conditionCode(),
        trace.fact(),
        messageKey);
  }
}
