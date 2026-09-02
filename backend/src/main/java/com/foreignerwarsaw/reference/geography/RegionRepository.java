package com.foreignerwarsaw.reference.geography;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, UUID> {

  List<Region> findByCountry_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(String countryCode);
}
