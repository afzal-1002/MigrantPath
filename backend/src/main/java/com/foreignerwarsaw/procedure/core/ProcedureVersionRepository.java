package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.PublicationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureVersionRepository extends JpaRepository<ProcedureVersion, UUID> {

  /**
   * {@code JOIN FETCH v.procedure} is required, not optional (the same Phase 3-class bug this
   * regression-tests): every admin-response DTO reads {@code version.getProcedure().getCode()}
   * after the transactional service method has returned - without the fetch, that's a {@code
   * LazyInitializationException} once the controller maps it.
   */
  @Query(
      "SELECT v FROM ProcedureVersion v JOIN FETCH v.procedure WHERE v.procedure.id = :procedureId AND v.versionNumber = :versionNumber")
  Optional<ProcedureVersion> findByProcedure_IdAndVersionNumber(
      @Param("procedureId") UUID procedureId, @Param("versionNumber") int versionNumber);

  /**
   * Same {@code JOIN FETCH v.procedure} requirement as {@link #findByProcedure_IdAndVersionNumber}
   * - the plain inherited {@code findById} leaves {@code procedure} as an uninitialized lazy proxy,
   * which is exactly the bug this method exists to avoid: every mutating service method
   * (submit/approve/publish/archive) re-fetches by id inside its own transaction (to avoid the
   * detached-entity bug - see {@code ProcedureVersionService#submitForReview}'s Javadoc) and then
   * returns the entity for the controller to map to a DTO that reads {@code
   * getProcedure().getCode()} after the transaction has closed.
   */
  @Query("SELECT v FROM ProcedureVersion v JOIN FETCH v.procedure WHERE v.id = :id")
  Optional<ProcedureVersion> findByIdFetchingProcedure(@Param("id") UUID id);

  /**
   * The one authoritative Active-Version Predicate implementation for procedure content
   * (docs/database/DATABASE.md §0, brief §9) - {@code EXCLUSIVE effectiveTo}, the legal-content
   * convention (deliberately different from reference data's inclusive {@code valid_to}, ADR-006).
   * Every other place that needs "the version a user should see right now" calls this - never a
   * bespoke "get the latest" query (brief: "one authoritative implementation... do not duplicate
   * slightly different active-version logic").
   */
  @Query(
      """
      SELECT v FROM ProcedureVersion v
      WHERE v.procedure.id = :procedureId
        AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
        AND v.effectiveFrom <= :evaluationDate
        AND (v.effectiveTo IS NULL OR v.effectiveTo > :evaluationDate)
      """)
  Optional<ProcedureVersion> findActivePublishedVersion(
      @Param("procedureId") UUID procedureId, @Param("evaluationDate") LocalDate evaluationDate);

  /**
   * Every PUBLISHED version of a procedure, regardless of date - used by the publish workflow to
   * find which existing version(s) a new publish must close (brief §58/§80), not by any user-facing
   * read path.
   */
  @Query(
      """
      SELECT v FROM ProcedureVersion v
      WHERE v.procedure.id = :procedureId AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
      """)
  List<ProcedureVersion> findPublishedVersions(@Param("procedureId") UUID procedureId);

  @Query(
      "SELECT COALESCE(MAX(v.versionNumber), 0) FROM ProcedureVersion v WHERE v.procedure.id = :procedureId")
  int findMaxVersionNumber(@Param("procedureId") UUID procedureId);

  List<ProcedureVersion> findByStatus(PublicationStatus status);
}
