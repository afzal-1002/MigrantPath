package com.foreignerwarsaw.reference.country;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, UUID> {

  Optional<Country> findByCodeIgnoreCase(String code);

  List<Country> findByActiveTrueOrderByDisplayOrderAsc();
}
