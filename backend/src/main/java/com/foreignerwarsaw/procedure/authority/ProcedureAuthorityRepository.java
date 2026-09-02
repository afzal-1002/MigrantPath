package com.foreignerwarsaw.procedure.authority;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureAuthorityRepository
    extends JpaRepository<ProcedureAuthority, ProcedureAuthorityId> {

  @Query(
      "SELECT pa FROM ProcedureAuthority pa JOIN FETCH pa.authority WHERE pa.procedure.id = :procedureId")
  List<ProcedureAuthority> findByProcedure_Id(@Param("procedureId") UUID procedureId);
}
