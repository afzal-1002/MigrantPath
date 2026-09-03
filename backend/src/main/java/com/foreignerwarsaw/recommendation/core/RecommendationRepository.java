package com.foreignerwarsaw.recommendation.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  /**
   * Added for Phase 8 (brief §54/§55): case creation needs the owning user (via the parent run) and
   * the procedure/version in one round trip, outside of any recommendation-run-scoped query.
   */
  @Query(
      """
      SELECT r FROM Recommendation r
      JOIN FETCH r.recommendationRun run JOIN FETCH run.user JOIN FETCH run.assessment
      JOIN FETCH r.procedure p JOIN FETCH p.category
      LEFT JOIN FETCH r.procedureVersion
      WHERE r.id = :id
      """)
  Optional<Recommendation> findByIdFetchingRunAndProcedure(@Param("id") UUID id);

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
