package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import com.foreignerwarsaw.recommendation.core.Recommendation;
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
 * A user's persistent, personal tracking of one pathway they chose to pursue (brief §2/§3) - bound
 * to exactly one {@link Recommendation} (unique FK, brief §53/§77 - the idempotency guarantee).
 * {@link #currentRevision} points at the {@link UserCaseSnapshotRevision} the user currently sees;
 * older revisions (created only by an explicit upgrade, brief §31/§32) remain in the database,
 * untouched, for historical reproducibility.
 *
 * <p>Never mutated by a later Procedure content change on its own (brief §2's core principle,
 * ADR-011) - only {@code UserCaseUpgradeService}'s explicit, user-triggered upgrade ever creates a
 * new revision.
 */
@Entity
@Table(name = "user_cases")
public class UserCase {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recommendation_id", nullable = false)
  private Recommendation recommendation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private Assessment assessment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_revision_id")
  private UserCaseSnapshotRevision currentRevision;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserCaseStatus status = UserCaseStatus.DRAFT;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @jakarta.persistence.Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected UserCase() {}

  public static UserCase create(
      User user,
      Recommendation recommendation,
      Assessment assessment,
      Procedure procedure,
      Instant now) {
    UserCase userCase = new UserCase();
    userCase.user = user;
    userCase.recommendation = recommendation;
    userCase.assessment = assessment;
    userCase.procedure = procedure;
    userCase.createdAt = now;
    userCase.updatedAt = now;
    return userCase;
  }

  public void attachRevision(UserCaseSnapshotRevision revision) {
    this.currentRevision = revision;
  }

  public void changeStatus(UserCaseStatus newStatus, Instant at) {
    UserCaseStatusTransitions.requireAllowed(this.status, newStatus);
    this.status = newStatus;
    this.updatedAt = at;
    if (newStatus == UserCaseStatus.SUBMITTED) {
      this.submittedAt = at;
    }
    if (newStatus == UserCaseStatus.COMPLETED) {
      this.completedAt = at;
    }
  }

  public void touch(Instant at) {
    this.updatedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Recommendation getRecommendation() {
    return recommendation;
  }

  public Assessment getAssessment() {
    return assessment;
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public UserCaseSnapshotRevision getCurrentRevision() {
    return currentRevision;
  }

  public UserCaseStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
