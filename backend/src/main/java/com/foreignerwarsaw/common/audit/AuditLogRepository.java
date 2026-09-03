package com.foreignerwarsaw.common.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  /**
   * Filtered/paginated audit listing (brief §64) - every filter is optional (a {@code NULL}
   * parameter matches everything for that column) so the admin UI can combine any subset of
   * actor/action/entity-type/date-range without a bespoke query per combination.
   */
  /**
   * {@code from}/{@code to} are never {@code null} here - {@link AuditService#search} substitutes a
   * wide-open default range before calling this method. An {@code (:from IS NULL OR ...)} pattern
   * (the style every other filter here uses) makes Postgres's JDBC driver unable to infer that
   * parameter's type ({@code "could not determine data type of parameter"}, a real failure found
   * running this query, not a hypothetical) when the parameter's only other appearance is inside
   * the same {@code OR} - {@code timestamptz} columns hit this in practice where String/UUID/enum
   * columns elsewhere in this same query did not.
   */
  @Query(
      """
      SELECT a FROM AuditLog a
      LEFT JOIN FETCH a.actor
      WHERE (:actorId IS NULL OR a.actor.id = :actorId)
        AND (:actionType IS NULL OR a.actionType = :actionType)
        AND (:entityType IS NULL OR a.entityType = :entityType)
        AND (:entityBusinessCode IS NULL OR a.entityBusinessCode = :entityBusinessCode)
        AND a.occurredAt >= :from
        AND a.occurredAt <= :to
      ORDER BY a.occurredAt DESC
      """)
  Page<AuditLog> search(
      @Param("actorId") UUID actorId,
      @Param("actionType") AuditActionType actionType,
      @Param("entityType") AuditEntityType entityType,
      @Param("entityBusinessCode") String entityBusinessCode,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
