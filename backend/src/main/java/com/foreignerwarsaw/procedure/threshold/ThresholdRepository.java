package com.foreignerwarsaw.procedure.threshold;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThresholdRepository extends JpaRepository<Threshold, UUID> {

  Optional<Threshold> findByCodeIgnoreCase(String code);
}
