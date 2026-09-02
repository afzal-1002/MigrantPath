package com.foreignerwarsaw.procedure.authority;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureVersionOfficeRepository
    extends JpaRepository<ProcedureVersionOffice, ProcedureVersionOfficeId> {

  @Query(
      "SELECT pvo FROM ProcedureVersionOffice pvo JOIN FETCH pvo.office o JOIN FETCH o.city WHERE pvo.procedureVersion.id = :procedureVersionId")
  List<ProcedureVersionOffice> findByProcedureVersion_Id(
      @Param("procedureVersionId") UUID procedureVersionId);
}
