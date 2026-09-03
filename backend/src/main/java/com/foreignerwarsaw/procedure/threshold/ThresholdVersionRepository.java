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

  /**
   * Phase 9 admin listing (brief §46/§47). {@link ThresholdVersion} has no {@code versionNumber}
   * column, unlike its three siblings (Procedure/Rule/QuestionnaireVersion) - a real, pre-existing
   * schema gap this phase does not close (see PHASE_9_REPORT.md "Deviations"); the admin UI
   * distinguishes threshold versions by status and effective date instead of a version number.
   */
  List<ThresholdVersion> findByThreshold_Id(UUID thresholdId);

  /** Phase 9 admin dashboard addition (brief §16). */
  long countByStatus(com.foreignerwarsaw.procedure.PublicationStatus status);

  /**
   * Phase 9 addition - fetch-joins {@code threshold} and every actor field {@code
   * AdminThresholdVersionResponse} reads, replacing the plain {@code findById} {@code
   * ThresholdService} previously used for every mutating call (submit/approve/archive/publish/
   * updateDraftContent) - the same LazyInitializationException class of bug fixed for Procedure and
   * Rule (see {@code ProcedureVersionRepository
   * #findByProcedure_IdAndVersionNumberFetchingActors}'s Javadoc).
   */
  @Query(
      """
      SELECT v FROM ThresholdVersion v
      JOIN FETCH v.threshold
      LEFT JOIN FETCH v.createdBy
      LEFT JOIN FETCH v.submittedBy
      LEFT JOIN FETCH v.approvedBy
      LEFT JOIN FETCH v.publishedBy
      WHERE v.id = :id
      """)
  Optional<ThresholdVersion> findByIdFetchingAll(@Param("id") UUID id);
}
