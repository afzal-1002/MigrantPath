package com.foreignerwarsaw.procedure.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureVersionSourceRepository
    extends JpaRepository<ProcedureVersionSource, ProcedureVersionSourceId> {

  @Query(
      """
      SELECT s FROM ProcedureVersionSource s
      JOIN FETCH s.officialSource os
      LEFT JOIN FETCH os.authority
      WHERE s.procedureVersion.id = :procedureVersionId
      """)
  List<ProcedureVersionSource> findByProcedureVersion_Id(
      @Param("procedureVersionId") UUID procedureVersionId);

  /** Phase 9 source-impact addition (brief §33/§34). */
  long countByOfficialSource_Id(UUID officialSourceId);

  /**
   * Pre-Phase-10 hardening addition (brief §C) - has this source ever backed a version that reached
   * {@code PUBLISHED} (including one since {@code ARCHIVED}, since the only path to {@code
   * ARCHIVED} is through {@code PUBLISHED})? Used by {@code
   * OfficialSourceService#assertIdentityEditable} to lock a source's legally-significant identity
   * fields once real provenance depends on them - see docs/admin/OFFICIAL_SOURCE_SAFETY.md.
   */
  @Query(
      "SELECT COUNT(s) > 0 FROM ProcedureVersionSource s"
          + " WHERE s.officialSource.id = :officialSourceId"
          + " AND s.procedureVersion.status IN (com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED,"
          + " com.foreignerwarsaw.procedure.PublicationStatus.ARCHIVED)")
  boolean existsUsedByPublishedVersion(@Param("officialSourceId") UUID officialSourceId);
}
