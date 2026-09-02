package com.foreignerwarsaw.reference.country;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryGroupRepository extends JpaRepository<CountryGroup, UUID> {

  Optional<CountryGroup> findByCode(String code);
}
