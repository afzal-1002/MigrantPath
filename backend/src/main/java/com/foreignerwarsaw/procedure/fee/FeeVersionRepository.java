package com.foreignerwarsaw.procedure.fee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeVersionRepository extends JpaRepository<FeeVersion, UUID> {

  @Query(
      "SELECT fv FROM FeeVersion fv JOIN FETCH fv.fee WHERE fv.procedureVersion.id = :procedureVersionId")
  List<FeeVersion> findByProcedureVersion_Id(@Param("procedureVersionId") UUID procedureVersionId);

  /**
   * Phase 9 addition (brief §25) - both fetch joins for the same reasons as StepVersionRepository.
   */
  @Query(
      "SELECT fv FROM FeeVersion fv JOIN FETCH fv.fee JOIN FETCH fv.procedureVersion WHERE fv.id = :id")
  java.util.Optional<FeeVersion> findByIdFetchingAll(@Param("id") UUID id);
}
