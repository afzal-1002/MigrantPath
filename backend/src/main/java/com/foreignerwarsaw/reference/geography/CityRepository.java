package com.foreignerwarsaw.reference.geography;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, UUID> {

  Optional<City> findByCodeIgnoreCase(String code);

  List<City> findByRegion_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(String regionCode);
}
