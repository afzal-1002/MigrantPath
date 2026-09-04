package com.foreignerwarsaw.admin.dto;

import com.foreignerwarsaw.admin.review.AdminReview;
import java.time.Instant;
import java.util.UUID;

public record AdminReviewResponse(
    UUID id,
    String entityType,
    UUID entityVersionId,
    String submittedByEmail,
    String reviewerEmail,
    String status,
    String comment,
    Instant createdAt,
    Instant completedAt) {

  // Phase 12: submittedBy/reviewer become null once that account is deleted (V48/ADR governance-
  // safe deletion) - never NPE, and never fabricate a live-looking email for a deleted account.
  // The pseudonymous submittedByActorRef still exists but is deliberately not surfaced here (an
  // internal UUID is not a meaningful display value); DELETED_ACCOUNT communicates the real state
  // plainly instead.
  private static final String DELETED_ACCOUNT_LABEL = "DELETED_ACCOUNT";

  public static AdminReviewResponse from(AdminReview review) {
    return new AdminReviewResponse(
        review.getId(),
        review.getEntityType().name(),
        review.getEntityVersionId(),
        review.getSubmittedBy() != null
            ? review.getSubmittedBy().getEmail()
            : DELETED_ACCOUNT_LABEL,
        review.getReviewer() != null ? review.getReviewer().getEmail() : null,
        review.getStatus().name(),
        review.getComment(),
        review.getCreatedAt(),
        review.getCompletedAt());
  }
}
