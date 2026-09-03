package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * The immutable pointer one snapshot pins (brief §6/§101/§105): exactly which {@link
 * ProcedureVersion} and {@code evaluationDate} its {@code UserCaseStep}/{@code
 * UserCaseDocument}/{@code UserCaseFee} rows were built from. Never edited once created - an
 * upgrade always inserts {@code revisionNumber + 1}, linking back via {@link #previousRevisionId}
 * (brief §32), never mutates an existing revision.
 */
@Entity
@Table(name = "user_case_snapshot_revisions")
public class UserCaseSnapshotRevision {

  /**
   * Bumped only if this class's own JSON-free relational snapshot shape changes incompatibly (brief
   * §8) - independent of {@code RecommendationService.ENGINE_VERSION} and {@code
   * RuleEvaluator.ENGINE_VERSION}.
   */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_case_id", nullable = false)
  private UserCase userCase;

  @Column(name = "revision_number", nullable = false)
  private int revisionNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_version_id", nullable = false)
  private ProcedureVersion procedureVersion;

  @Column(name = "evaluation_date", nullable = false)
  private LocalDate evaluationDate;

  @Column(name = "snapshot_schema_version", nullable = false)
  private int snapshotSchemaVersion = CURRENT_SCHEMA_VERSION;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SnapshotRevisionReason reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "previous_revision_id")
  private UserCaseSnapshotRevision previousRevision;

  protected UserCaseSnapshotRevision() {}

  public static UserCaseSnapshotRevision create(
      UserCase userCase,
      int revisionNumber,
      ProcedureVersion procedureVersion,
      LocalDate evaluationDate,
      SnapshotRevisionReason reason,
      User createdBy,
      UserCaseSnapshotRevision previousRevision,
      Instant now) {
    UserCaseSnapshotRevision revision = new UserCaseSnapshotRevision();
    revision.userCase = userCase;
    revision.revisionNumber = revisionNumber;
    revision.procedureVersion = procedureVersion;
    revision.evaluationDate = evaluationDate;
    revision.reason = reason;
    revision.createdBy = createdBy;
    revision.previousRevision = previousRevision;
    revision.createdAt = now;
    return revision;
  }

  public UUID getId() {
    return id;
  }

  public UserCase getUserCase() {
    return userCase;
  }

  public int getRevisionNumber() {
    return revisionNumber;
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public LocalDate getEvaluationDate() {
    return evaluationDate;
  }

  public int getSnapshotSchemaVersion() {
    return snapshotSchemaVersion;
  }

  public SnapshotRevisionReason getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public UserCaseSnapshotRevision getPreviousRevision() {
    return previousRevision;
  }
}
