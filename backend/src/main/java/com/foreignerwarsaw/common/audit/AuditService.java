package com.foreignerwarsaw.common.audit;

import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write path for {@link AuditLog} (brief §60/§107) - every admin controller/service that
 * mutates governed content calls {@link #record} as part of the same transaction as the mutation
 * itself, so a rolled-back mutation never leaves behind a misleadingly "successful" audit row
 * (brief §107: "if publish fails, do not record misleading PUBLISHED").
 */
@Service
public class AuditService {

  private final AuditLogRepository auditLogRepository;
  private final Clock clock;

  public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
    this.auditLogRepository = auditLogRepository;
    this.clock = clock;
  }

  @Transactional
  public void record(
      User actor,
      AuditActionType actionType,
      AuditEntityType entityType,
      UUID entityId,
      String entityBusinessCode,
      UUID entityVersionId,
      String summary) {
    record(
        actor,
        actionType,
        entityType,
        entityId,
        entityBusinessCode,
        entityVersionId,
        summary,
        null);
  }

  @Transactional
  public void record(
      User actor,
      AuditActionType actionType,
      AuditEntityType entityType,
      UUID entityId,
      String entityBusinessCode,
      UUID entityVersionId,
      String summary,
      String metadataJson) {
    auditLogRepository.save(
        new AuditLog(
            actor,
            actionType,
            entityType,
            entityId,
            entityBusinessCode,
            entityVersionId,
            clock.instant(),
            summary,
            metadataJson));
  }

  private static final java.time.Instant DISTANT_PAST =
      java.time.Instant.parse("1970-01-01T00:00:00Z");
  private static final java.time.Instant DISTANT_FUTURE =
      java.time.Instant.parse("9999-12-31T23:59:59Z");

  /**
   * {@code from}/{@code to} default to a wide-open range rather than an {@code IS NULL} branch in
   * the query itself - see {@link AuditLogRepository#search}'s Javadoc for why.
   */
  @Transactional(readOnly = true)
  public Page<AuditLog> search(
      UUID actorId,
      AuditActionType actionType,
      AuditEntityType entityType,
      String entityBusinessCode,
      java.time.Instant from,
      java.time.Instant to,
      Pageable pageable) {
    return auditLogRepository.search(
        actorId,
        actionType,
        entityType,
        entityBusinessCode,
        from != null ? from : DISTANT_PAST,
        to != null ? to : DISTANT_FUTURE,
        pageable);
  }
}
