package com.foreignerwarsaw.usercase.core;

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

/**
 * Append-only case timeline entry (brief §24/§25) - never edited after insert. {@link #metadata} is
 * small and non-sensitive only (brief §83) - a status transition or a stable item code, never a raw
 * answer/note value.
 */
@Entity
@Table(name = "user_case_events")
public class UserCaseEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_case_id", nullable = false)
  private UserCase userCase;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 40)
  private UserCaseEventType eventType;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_user_id")
  private User actor;

  @Column(length = 500)
  private String metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserCaseEvent() {}

  public UserCaseEvent(
      UserCase userCase,
      UserCaseEventType eventType,
      Instant occurredAt,
      User actor,
      String metadata) {
    this.userCase = userCase;
    this.eventType = eventType;
    this.occurredAt = occurredAt;
    this.actor = actor;
    this.metadata = metadata;
    this.createdAt = occurredAt;
  }

  public UUID getId() {
    return id;
  }

  public UserCase getUserCase() {
    return userCase;
  }

  public UserCaseEventType getEventType() {
    return eventType;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public User getActor() {
    return actor;
  }

  public String getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
