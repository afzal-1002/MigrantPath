package com.foreignerwarsaw.procedure.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureCategoryRepository extends JpaRepository<ProcedureCategory, UUID> {

  Optional<ProcedureCategory> findByCodeIgnoreCase(String code);

  List<ProcedureCategory> findByActiveTrueOrderByDisplayOrderAsc();
}
