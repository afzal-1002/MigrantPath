package com.foreignerwarsaw.procedure.source;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceVerificationRepository extends JpaRepository<SourceVerification, UUID> {

  List<SourceVerification> findByOfficialSource_IdOrderByCheckedAtDesc(UUID officialSourceId);
}
