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

  /**
   * Phase 9 impact analysis (brief §72/§73) - how many active user cases currently depend on this
   * exact {@code ProcedureVersion} right now, i.e. it's still their <em>current</em> revision's
   * pinned version (not merely referenced by some superseded historical revision). Counts only -
   * never returns which users (brief §72's "do not expose private user details").
   */
  @Query(
      "SELECT COUNT(DISTINCT r.userCase.id) FROM UserCaseSnapshotRevision r"
          + " WHERE r.procedureVersion.id = :procedureVersionId AND r.userCase.currentRevision = r")
  long countActiveCasesOnProcedureVersion(@Param("procedureVersionId") UUID procedureVersionId);
}
