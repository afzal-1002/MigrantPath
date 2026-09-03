package com.foreignerwarsaw.procedure.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRequirementVersionRepository
    extends JpaRepository<DocumentRequirementVersion, UUID> {

  /**
   * {@code documentType} is LEFT JOIN FETCH'd (nullable) - the same to-one-only fetch discipline as
   * StepVersionRepository (brief §99/§100).
   */
  @Query(
      """
      SELECT drv FROM DocumentRequirementVersion drv
      JOIN FETCH drv.documentRequirement dr
      LEFT JOIN FETCH dr.documentType
      WHERE drv.procedureVersion.id = :procedureVersionId
      ORDER BY drv.sortOrder ASC
      """)
  List<DocumentRequirementVersion> findByProcedureVersion_IdOrderBySortOrderAsc(
      @Param("procedureVersionId") UUID procedureVersionId);

  /**
   * Phase 9 addition (brief §23) - both fetch joins for the same reasons as StepVersionRepository.
   */
  @Query(
      """
      SELECT drv FROM DocumentRequirementVersion drv
      JOIN FETCH drv.documentRequirement dr
      LEFT JOIN FETCH dr.documentType
      JOIN FETCH drv.procedureVersion
      WHERE drv.id = :id
      """)
  java.util.Optional<DocumentRequirementVersion> findByIdFetchingAll(@Param("id") UUID id);
}
