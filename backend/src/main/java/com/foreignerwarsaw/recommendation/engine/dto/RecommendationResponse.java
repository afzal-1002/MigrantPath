package com.foreignerwarsaw.recommendation.engine.dto;

import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import java.util.List;
import java.util.UUID;

/**
 * Brief §41's exact response shape plus {@link #id} (added for Phase 8, brief §4 of the User Cases
 * brief: "Start this pathway" needs the Recommendation's own id to call {@code POST
 * /api/v1/recommendations/{recommendationId}/cases} - never a raw condition trace, never a
 * confidence number).
 */
public record RecommendationResponse(
    UUID id,
    String procedureCode,
    String procedureTitle,
    String recommendationType,
    int rank,
    List<RecommendationReasonResponse> reasons,
    List<String> missingFacts,
    List<SourceResponse> officialSources) {}
