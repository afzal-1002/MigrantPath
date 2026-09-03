package com.foreignerwarsaw.common.audit;

import com.foreignerwarsaw.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only administrative audit record (brief §60-§64). Written only by {@link
 * AuditService#record} - never updated or deleted by any application code once persisted (brief
 * §62's "audit rows append-only... no Admin UI edit/delete"); the entity exposes no setters beyond
 * its constructor for exactly that reason.
 *
 * <p>{@code metadata} is deliberately minimal, structured JSON, never a dump of an entity graph
 * (brief §111) - a before/after value pair for the one or two fields that changed, e.g. {@code
 * {"from":"DRAFT","to":"IN_REVIEW"}}. Never a password hash, session id, token, or a user's
 * Assessment answer (brief §61/§111/§133).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_user_id")
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 60)
  private AuditActionType actionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 40)
  private AuditEntityType entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @Column(name = "entity_business_code", length = 50)
  private String entityBusinessCode;

  @Column(name = "entity_version_id")
  private UUID entityVersionId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 500)
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String metadata;

  protected AuditLog() {}

  public AuditLog(
      User actor,
      AuditActionType actionType,
      AuditEntityType entityType,
      UUID entityId,
      String entityBusinessCode,
      UUID entityVersionId,
      Instant occurredAt,
      String summary,
      String metadata) {
    this.actor = actor;
    this.actionType = actionType;
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityBusinessCode = entityBusinessCode;
    this.entityVersionId = entityVersionId;
    this.occurredAt = occurredAt;
    this.summary = summary;
    this.metadata = metadata;
  }

  public UUID getId() {
    return id;
  }

  public User getActor() {
    return actor;
  }

  public AuditActionType getActionType() {
    return actionType;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getEntityBusinessCode() {
    return entityBusinessCode;
  }

  public UUID getEntityVersionId() {
    return entityVersionId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getSummary() {
    return summary;
  }

  public String getMetadata() {
    return metadata;
  }
}
