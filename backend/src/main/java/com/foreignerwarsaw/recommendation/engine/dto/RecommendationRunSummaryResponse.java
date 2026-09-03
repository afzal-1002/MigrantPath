package com.foreignerwarsaw.recommendation.engine.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * History-list row (brief §81: "return summaries... do not return massive rule traces in history
 * list").
 */
public record RecommendationRunSummaryResponse(
    UUID id,
    LocalDate evaluationDate,
    String status,
    Instant createdAt,
    Instant completedAt,
    int recommendationCount,
    int primaryMatchCount) {}
