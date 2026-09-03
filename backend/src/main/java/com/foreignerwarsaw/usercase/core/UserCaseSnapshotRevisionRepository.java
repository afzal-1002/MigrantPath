package com.foreignerwarsaw.usercase.core;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCaseSnapshotRevisionRepository
    extends JpaRepository<UserCaseSnapshotRevision, UUID> {

  @Query(
      "SELECT COALESCE(MAX(r.revisionNumber), 0) FROM UserCaseSnapshotRevision r WHERE r.userCase.id = :userCaseId")
  int findMaxRevisionNumber(@Param("userCaseId") UUID userCaseId);

  @Query("SELECT r FROM UserCaseSnapshotRevision r JOIN FETCH r.procedureVersion WHERE r.id = :id")
  Optional<UserCaseSnapshotRevision> findByIdFetchingProcedureVersion(@Param("id") UUID id);
}
