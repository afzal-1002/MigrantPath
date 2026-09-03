package com.foreignerwarsaw.usercase.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCaseFeeRepository extends JpaRepository<UserCaseFee, UUID> {

  List<UserCaseFee> findBySnapshotRevision_IdOrderBySortOrderAsc(UUID snapshotRevisionId);

  Optional<UserCaseFee> findBySnapshotRevision_IdAndStableCode(
      UUID snapshotRevisionId, String stableCode);
}
