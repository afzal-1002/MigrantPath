package com.foreignerwarsaw.procedure.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

  Optional<DocumentType> findByCodeIgnoreCase(String code);
}
