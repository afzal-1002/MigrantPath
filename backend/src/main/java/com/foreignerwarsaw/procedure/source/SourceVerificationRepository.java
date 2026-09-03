package com.foreignerwarsaw.procedure.source;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceVerificationRepository extends JpaRepository<SourceVerification, UUID> {

  /**
   * Phase 9 addition - fetch-joins {@code checkedBy}, read by {@code SourceVerificationResponse
   * #from} after this repository call's own transaction has closed (the same
   * LazyInitializationException class of bug fixed throughout Phase 9's other admin repositories -
   * see {@code ProcedureVersionRepository #findByProcedure_IdAndVersionNumberFetchingActors}'s
   * Javadoc).
   */
  @Query(
      "SELECT v FROM SourceVerification v LEFT JOIN FETCH v.checkedBy"
          + " WHERE v.officialSource.id = :officialSourceId ORDER BY v.checkedAt DESC")
  List<SourceVerification> findByOfficialSource_IdOrderByCheckedAtDesc(
      @Param("officialSourceId") UUID officialSourceId);
}
