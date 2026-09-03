package com.foreignerwarsaw.recommendation.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationReasonRepository extends JpaRepository<RecommendationReason, UUID> {

  List<RecommendationReason> findByRecommendation_IdOrderByDisplayOrderAsc(UUID recommendationId);

  /**
   * Batched read for a whole run's reasons in one query (brief §77/§85 - avoid N+1 across every
   * recommendation in a run), grouped by recommendation id by the caller.
   */
  List<RecommendationReason>
      findByRecommendation_RecommendationRun_IdOrderByRecommendation_IdAscDisplayOrderAsc(
          UUID recommendationRunId);
}
