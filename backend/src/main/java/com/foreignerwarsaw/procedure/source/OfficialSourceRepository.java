package com.foreignerwarsaw.procedure.source;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialSourceRepository extends JpaRepository<OfficialSource, UUID> {

  Optional<OfficialSource> findByIdAndActiveTrue(UUID id);

  /** Phase 9 admin dashboard addition (brief §16/§35). */
  long countByVerificationStatus(VerificationStatus verificationStatus);
}
