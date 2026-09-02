package com.foreignerwarsaw.reference.geography;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, UUID> {

  List<Region> findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(String countryCode);

  /**
   * Added for Phase 5's REGION-typed answer validation (brief §27) - no other Phase 3 caller needed
   * a single-code lookup before now.
   */
  Optional<Region> findByCodeIgnoreCase(String code);
}
