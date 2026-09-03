package com.foreignerwarsaw.procedure.step;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StepVersionRepository extends JpaRepository<StepVersion, UUID> {

  /**
   * Deterministic ordering by sort_order, never database id (brief §14). {@code JOIN FETCH
   * sv.procedureStep} avoids the Phase 3-class LazyInitializationException once the DTO mapping
   * runs outside this transaction (brief §99/§100) - a single to-one fetch join, never combined
   * with another collection join in the same query (no Cartesian risk).
   */
  @Query(
      "SELECT sv FROM StepVersion sv JOIN FETCH sv.procedureStep WHERE sv.procedureVersion.id = :procedureVersionId ORDER BY sv.sortOrder ASC")
  List<StepVersion> findByProcedureVersion_IdOrderBySortOrderAsc(
      @Param("procedureVersionId") UUID procedureVersionId);

  /**
   * Phase 9 addition (brief §21) - both fetch joins are required: {@code procedureStep} for {@code
   * StepResponse#from}'s stable code, {@code procedureVersion} for {@code
   * ProcedureStepService#updateStep}/{@code removeStep}'s own DRAFT-only guard.
   */
  @Query(
      "SELECT sv FROM StepVersion sv JOIN FETCH sv.procedureStep JOIN FETCH sv.procedureVersion WHERE sv.id = :id")
  java.util.Optional<StepVersion> findByIdFetchingAll(@Param("id") UUID id);
}
