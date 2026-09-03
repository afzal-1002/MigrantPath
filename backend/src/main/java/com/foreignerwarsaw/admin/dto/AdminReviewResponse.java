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

  public static AdminReviewResponse from(AdminReview review) {
    return new AdminReviewResponse(
        review.getId(),
        review.getEntityType().name(),
        review.getEntityVersionId(),
        review.getSubmittedBy().getEmail(),
        review.getReviewer() != null ? review.getReviewer().getEmail() : null,
        review.getStatus().name(),
        review.getComment(),
        review.getCreatedAt(),
        review.getCompletedAt());
  }
}
