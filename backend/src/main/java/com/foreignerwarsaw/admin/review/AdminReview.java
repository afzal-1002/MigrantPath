package com.foreignerwarsaw.admin.review;

import com.foreignerwarsaw.common.audit.AuditEntityType;
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
 * A formal review record for one content version (brief §66), entity-agnostic by design (one table
 * for Procedure/Rule/Threshold/Questionnaire versions alike, keyed by {@code entityType} + {@code
 * entityVersionId} rather than a foreign key into any one specific version table) - deliberately
 * the one place this codebase centralizes review bookkeeping across four otherwise independent
 * domain lifecycles (brief §6's explicit invitation to extract shared "status-transition
 * conventions, actor/timestamp handling" while keeping each domain's own publish-readiness
 * validation domain-specific and untouched). See {@link ContentReviewCoordinator}.
 *
 * <p>{@code submittedBy} is denormalized from whichever version's {@code submittedBy} field
 * triggered this review, specifically so {@link ContentReviewCoordinator#approve} can enforce
 * self-approval prevention (brief §5/§117) without re-fetching four different entity types
 * generically.
 */
@Entity
@Table(name = "admin_review")
public class AdminReview {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 40)
  private AuditEntityType entityType;

  @Column(name = "entity_version_id", nullable = false)
  private UUID entityVersionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "submitted_by", nullable = false)
  private User submittedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewer")
  private User reviewer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdminReviewStatus status = AdminReviewStatus.PENDING;

  @Column(length = 2000)
  private String comment;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected AdminReview() {}

  public static AdminReview open(
      AuditEntityType entityType, UUID entityVersionId, User submittedBy, Instant now) {
    AdminReview review = new AdminReview();
    review.entityType = entityType;
    review.entityVersionId = entityVersionId;
    review.submittedBy = submittedBy;
    review.createdAt = now;
    return review;
  }

  public UUID getId() {
    return id;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public UUID getEntityVersionId() {
    return entityVersionId;
  }

  public User getSubmittedBy() {
    return submittedBy;
  }

  public User getReviewer() {
    return reviewer;
  }

  public AdminReviewStatus getStatus() {
    return status;
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void complete(AdminReviewStatus status, User reviewer, String comment, Instant at) {
    this.status = status;
    this.reviewer = reviewer;
    this.comment = comment;
    this.completedAt = at;
  }
}
