package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.recommendation.core.RecommendationReasonType;
import java.util.UUID;

/**
 * A not-yet-persisted {@code RecommendationReason} (brief §10) - {@code
 * RecommendationReasonMapper}'s output, before {@code RecommendationService} assigns a real {@code
 * displayOrder} and saves it against a real {@code Recommendation}.
 */
public record ReasonDraft(
    RecommendationReasonType type,
    String reasonCode,
    UUID ruleVersionId,
    String conditionCode,
    String factCode,
    String messageKey) {}
