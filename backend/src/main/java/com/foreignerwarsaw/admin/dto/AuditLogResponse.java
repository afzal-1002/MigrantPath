package com.foreignerwarsaw.admin.dto;

import com.foreignerwarsaw.common.audit.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    String actorEmail,
    String actionType,
    String entityType,
    UUID entityId,
    String entityBusinessCode,
    UUID entityVersionId,
    Instant occurredAt,
    String summary) {

  public static AuditLogResponse from(AuditLog log) {
    return new AuditLogResponse(
        log.getId(),
        log.getActor() != null ? log.getActor().getEmail() : null,
        log.getActionType().name(),
        log.getEntityType().name(),
        log.getEntityId(),
        log.getEntityBusinessCode(),
        log.getEntityVersionId(),
        log.getOccurredAt(),
        log.getSummary());
  }
}
