package com.foreignerwarsaw.recommendation.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  @Query(
      """
      SELECT r FROM Recommendation r
      JOIN FETCH r.procedure p JOIN FETCH p.category
      LEFT JOIN FETCH r.procedureVersion
      WHERE r.recommendationRun.id = :runId
      ORDER BY r.rank ASC
      """)
  List<Recommendation> findByRecommendationRun_IdOrderByRankAsc(@Param("runId") UUID runId);
}
