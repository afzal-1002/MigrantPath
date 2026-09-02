package com.foreignerwarsaw.reference.geography;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {

  Optional<Jurisdiction> findByCode(String code);
}
