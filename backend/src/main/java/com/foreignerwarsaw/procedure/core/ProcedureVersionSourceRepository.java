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
}
