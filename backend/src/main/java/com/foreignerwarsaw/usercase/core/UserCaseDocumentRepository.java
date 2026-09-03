package com.foreignerwarsaw.usercase.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCaseDocumentRepository extends JpaRepository<UserCaseDocument, UUID> {

  List<UserCaseDocument> findBySnapshotRevision_IdOrderBySortOrderAsc(UUID snapshotRevisionId);

  Optional<UserCaseDocument> findBySnapshotRevision_IdAndStableCode(
      UUID snapshotRevisionId, String stableCode);
}
