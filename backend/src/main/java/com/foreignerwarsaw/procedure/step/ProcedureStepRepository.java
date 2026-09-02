package com.foreignerwarsaw.procedure.step;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureStepRepository extends JpaRepository<ProcedureStep, UUID> {

  Optional<ProcedureStep> findByProcedure_IdAndStableCode(UUID procedureId, String stableCode);
}
