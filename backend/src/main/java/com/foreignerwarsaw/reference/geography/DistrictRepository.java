package com.foreignerwarsaw.reference.geography;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, UUID> {

  List<District> findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(String cityCode);

  /**
   * Added for Phase 5's DISTRICT-typed answer validation (brief §27) - no other Phase 3 caller
   * needed a single-code lookup before now.
   */
  Optional<District> findByCodeIgnoreCase(String code);
}
