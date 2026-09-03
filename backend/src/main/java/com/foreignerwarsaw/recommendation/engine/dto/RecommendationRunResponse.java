package com.foreignerwarsaw.recommendation.engine.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The full detail response for one {@code RecommendationRun} (brief §41/§82's "detail response").
 */
public record RecommendationRunResponse(
    UUID id,
    UUID assessmentId,
    LocalDate evaluationDate,
    String status,
    String recommendationEngineVersion,
    String ruleEngineVersion,
    Instant createdAt,
    Instant completedAt,
    List<RecommendationResponse> recommendations) {}
