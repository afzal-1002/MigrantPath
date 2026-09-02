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
}
