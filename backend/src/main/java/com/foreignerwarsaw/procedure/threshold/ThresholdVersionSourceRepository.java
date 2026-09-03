package com.foreignerwarsaw.procedure.threshold;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThresholdVersionSourceRepository
    extends JpaRepository<ThresholdVersionSource, ThresholdVersionSourceId> {

  /**
   * Added for Phase 7 (brief §30/§77): resolving a recommendation's combined official-source list
   * needs every source backing a threshold a rule actually used, not just the rule's own sources.
   */
  List<ThresholdVersionSource> findByThresholdVersion_Id(UUID thresholdVersionId);

  /**
   * Pre-Phase-10 hardening addition (brief §D) - fetch-joins {@code officialSource}, read by {@code
   * AdminThresholdVersionResponse.Source#from} after this repository call's own transaction has
   * closed (the same LazyInitializationException class of bug fixed throughout Phase 9's other
   * admin repositories). A new method rather than widening {@link #findByThresholdVersion_Id}
   * itself, since that one's existing Phase 7 caller doesn't need the extra join.
   */
  @Query(
      "SELECT s FROM ThresholdVersionSource s JOIN FETCH s.officialSource WHERE s.thresholdVersion.id = :thresholdVersionId")
  List<ThresholdVersionSource> findByThresholdVersion_IdFetchingSource(
      @Param("thresholdVersionId") UUID thresholdVersionId);

  /** Phase 9 source-impact addition (brief §33/§34). */
  long countByOfficialSource_Id(UUID officialSourceId);

  /**
   * Pre-Phase-10 hardening addition (brief §C) - see {@code ProcedureVersionSourceRepository
   * #existsUsedByPublishedVersion}'s Javadoc for the full rationale.
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT COUNT(s) > 0 FROM ThresholdVersionSource s"
          + " WHERE s.officialSource.id = :officialSourceId"
          + " AND s.thresholdVersion.status IN (com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED,"
          + " com.foreignerwarsaw.procedure.PublicationStatus.ARCHIVED)")
  boolean existsUsedByPublishedVersion(
      @org.springframework.data.repository.query.Param("officialSourceId") UUID officialSourceId);
}
