package com.foreignerwarsaw.recommendation.core;

/**
 * Machine-readable reason kinds (brief §10) - a normal user never sees the raw condition trace
 * behind these (brief §12), only translated copy keyed by {@link
 * RecommendationReason#getMessageKey()}.
 */
public enum RecommendationReasonType {
  MATCHED_CONDITION,
  FAILED_CONDITION,
  MISSING_INFORMATION,
  EXCLUSION,
  ALTERNATIVE_PATH,
  PROCEDURE_PRIORITY,
  /**
   * A Rule targeting this procedure returned ERROR (brief §48) - carries no condition/fact code,
   * only enough to explain "we couldn't analyse this."
   */
  ANALYSIS_ERROR
}
