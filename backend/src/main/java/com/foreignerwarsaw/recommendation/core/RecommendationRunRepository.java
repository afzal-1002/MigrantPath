package com.foreignerwarsaw.recommendation.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, UUID> {

  @Query(
      "SELECT r FROM RecommendationRun r JOIN FETCH r.assessment JOIN FETCH r.user WHERE r.id = :id")
  Optional<RecommendationRun> findByIdFetchingAssessmentAndUser(@Param("id") UUID id);

  /** Most recent run for one assessment, regardless of status - "latest" per brief §40/§79. */
  Optional<RecommendationRun> findFirstByAssessment_IdOrderByCreatedAtDesc(UUID assessmentId);

  /** History listing (brief §66/§81), most recent first. */
  List<RecommendationRun> findByAssessment_IdOrderByCreatedAtDesc(UUID assessmentId);

  /**
   * Phase 12 personal-data export (brief §20) - every run for the account, across all assessments.
   */
  List<RecommendationRun> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}
