package com.foreignerwarsaw.admin.review;

/** Brief §66 - independent of {@code PublicationStatus}: a review record's own outcome. */
public enum AdminReviewStatus {
  PENDING,
  APPROVED,
  CHANGES_REQUESTED,
  REJECTED
}
