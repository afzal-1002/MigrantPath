package com.foreignerwarsaw.procedure.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, UUID> {

  Optional<DocumentRequirement> findByProcedure_IdAndStableCode(
      UUID procedureId, String stableCode);
}
