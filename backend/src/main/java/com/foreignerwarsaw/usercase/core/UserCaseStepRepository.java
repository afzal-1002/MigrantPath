package com.foreignerwarsaw.usercase.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCaseStepRepository extends JpaRepository<UserCaseStep, UUID> {

  List<UserCaseStep> findBySnapshotRevision_IdOrderBySortOrderAsc(UUID snapshotRevisionId);

  Optional<UserCaseStep> findBySnapshotRevision_IdAndStableCode(
      UUID snapshotRevisionId, String stableCode);
}
