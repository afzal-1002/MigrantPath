package com.foreignerwarsaw.recommendation.engine.dto;

import com.foreignerwarsaw.recommendation.core.RecommendationReason;

/**
 * Brief §10/§11's exact machine-readable shape - {@code messageKey} is what the frontend
 * translates, never English text from the backend.
 */
public record RecommendationReasonResponse(
    String reasonType, String reasonCode, String messageKey, String factCode) {

  public static RecommendationReasonResponse from(RecommendationReason reason) {
    return new RecommendationReasonResponse(
        reason.getReasonType().name(),
        reason.getReasonCode(),
        reason.getMessageKey(),
        reason.getFactCode());
  }
}
