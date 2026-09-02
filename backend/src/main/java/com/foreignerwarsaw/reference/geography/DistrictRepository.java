package com.foreignerwarsaw.reference.geography;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, UUID> {

  List<District> findByCity_CodeIgnoreCaseAndActiveTrueOrderByCanonicalNameAsc(String cityCode);
}
