package com.foreignerwarsaw.procedure.threshold;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThresholdVersionRepository extends JpaRepository<ThresholdVersion, UUID> {

  /**
   * Same authoritative, exclusive-effectiveTo Active-Version Predicate as {@code
   * ProcedureVersionRepository#findActivePublishedVersion} (brief §9) - one implementation per
   * versioned entity, never a bespoke query duplicated elsewhere.
   */
  @Query(
      """
      SELECT v FROM ThresholdVersion v
      WHERE v.threshold.id = :thresholdId
        AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
        AND v.effectiveFrom <= :evaluationDate
        AND (v.effectiveTo IS NULL OR v.effectiveTo > :evaluationDate)
      """)
  Optional<ThresholdVersion> findActivePublishedVersion(
      @Param("thresholdId") UUID thresholdId, @Param("evaluationDate") LocalDate evaluationDate);

  @Query(
      """
      SELECT v FROM ThresholdVersion v
      WHERE v.threshold.id = :thresholdId AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
      """)
  List<ThresholdVersion> findPublishedVersions(@Param("thresholdId") UUID thresholdId);
}
