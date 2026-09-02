package com.foreignerwarsaw.procedure.fee;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeRepository extends JpaRepository<Fee, UUID> {

  Optional<Fee> findByProcedure_IdAndStableCode(UUID procedureId, String stableCode);
}
