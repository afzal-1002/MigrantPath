package com.foreignerwarsaw.recommendation.core;

/**
 * Per-procedure recommendation category (brief §3-§8, §48) - deliberately never a percentage or
 * confidence score. {@link #UNAVAILABLE_FOR_ANALYSIS} is what a Phase 6 {@code ERROR} or a missing
 * active {@code ProcedureVersion} becomes here - never silently folded into {@link #NOT_APPLICABLE}
 * (brief §8/§48/§118 of the two briefs this codebase has followed).
 */
public enum RecommendationType {
  /**
   * Required rules SATISFIED, no exclusion applies, ranked among the strongest matches. Never
   * "guaranteed eligible" - see docs/recommendations/RECOMMENDATION_POLICY.md for the exact
   * user-facing language contract.
   */
  PRIMARY_MATCH,
  /**
   * Applicable, but ranked behind at least one stronger match for the same or an overlapping need.
   */
  POSSIBLE_ALTERNATIVE,
  /**
   * A required (or exclusion) rule is INDETERMINATE - not yet known whether this pathway applies.
   */
  MORE_INFORMATION_REQUIRED,
  /** A required rule clearly FAILED, or an exclusion rule is SATISFIED, based on known facts. */
  NOT_APPLICABLE,
  /**
   * A Rule targeting this procedure returned ERROR, or no active PUBLISHED ProcedureVersion exists
   * for the evaluation date - a system/content problem, never presented as a confident result of
   * any other kind.
   */
  UNAVAILABLE_FOR_ANALYSIS
}
