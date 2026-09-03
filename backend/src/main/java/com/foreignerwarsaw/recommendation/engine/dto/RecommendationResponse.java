package com.foreignerwarsaw.recommendation.engine.dto;

import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import java.util.List;

/** Brief §41's exact response shape - never a raw condition trace, never a confidence number. */
public record RecommendationResponse(
    String procedureCode,
    String procedureTitle,
    String recommendationType,
    int rank,
    List<RecommendationReasonResponse> reasons,
    List<String> missingFacts,
    List<SourceResponse> officialSources) {}
