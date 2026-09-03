package com.foreignerwarsaw.procedure.threshold;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThresholdVersionSourceRepository
    extends JpaRepository<ThresholdVersionSource, ThresholdVersionSourceId> {

  /**
   * Added for Phase 7 (brief §30/§77): resolving a recommendation's combined official-source list
   * needs every source backing a threshold a rule actually used, not just the rule's own sources.
   */
  List<ThresholdVersionSource> findByThresholdVersion_Id(UUID thresholdVersionId);

  /** Phase 9 source-impact addition (brief §33/§34). */
  long countByOfficialSource_Id(UUID officialSourceId);
}
