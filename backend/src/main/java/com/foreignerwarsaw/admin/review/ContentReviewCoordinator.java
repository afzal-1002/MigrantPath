package com.foreignerwarsaw.admin.review;

import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place review bookkeeping and separation-of-duties are enforced, shared by every
 * Procedure/Rule/Threshold/Questionnaire admin service (brief §5/§6/§66/§117) - the domain-specific
 * publish-readiness validation each of those four keeps living in its own {@code
 * *PublishingService}/{@code *Service} is untouched by this class.
 *
 * <p><b>Separation-of-duties policy actually implemented</b> (brief §5's documented choice, since
 * strict {@code creator != approver != publisher} was judged disproportionate for this MVP - only
 * {@code creator/submitter != approver} is enforced): the creator of a version may submit it, but
 * whoever reviews it (approves or requests changes) must be a different account than whoever
 * submitted it (checked here, in {@link #approve} and {@link #requestChanges}) - never the same
 * person self-approving their own submission. {@code ADMIN} may still both approve <em>and</em>
 * publish the same version (publish is a separate, ADMIN-only-gated action at the controller/
 * SecurityConfig level - see docs/admin/ROLE_PERMISSIONS.md), which is the brief's own documented
 * fallback ("if strict separation causes disproportionate MVP complexity... ADMIN may publish
 * approved content").
 */
@Service
public class ContentReviewCoordinator {

  private final AdminReviewRepository adminReviewRepository;
  private final Clock clock;

  public ContentReviewCoordinator(AdminReviewRepository adminReviewRepository, Clock clock) {
    this.adminReviewRepository = adminReviewRepository;
    this.clock = clock;
  }

  /** Called immediately after a version's own {@code submitForReview} transition succeeds. */
  @Transactional
  public AdminReview openReview(
      AuditEntityType entityType, UUID entityVersionId, User submittedBy) {
    if (adminReviewRepository
        .findByEntityTypeAndEntityVersionIdAndStatus(
            entityType, entityVersionId, AdminReviewStatus.PENDING)
        .isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "REVIEW_ALREADY_PENDING",
          "A review is already pending for this version");
    }
    return adminReviewRepository.save(
        AdminReview.open(entityType, entityVersionId, submittedBy, clock.instant()));
  }

  /**
   * Enforces creator/submitter != reviewer (brief §5/§117) before returning the now-closed review -
   * callers still separately invoke the version's own domain {@code approve(...)} transition; this
   * method only owns the review record and the separation-of-duties check.
   */
  @Transactional
  public AdminReview approve(
      AuditEntityType entityType, UUID entityVersionId, User reviewer, String comment) {
    AdminReview review = requirePending(entityType, entityVersionId);
    requireNotSelfReview(review, reviewer);
    review.complete(AdminReviewStatus.APPROVED, reviewer, comment, clock.instant());
    return review;
  }

  @Transactional
  public AdminReview requestChanges(
      AuditEntityType entityType, UUID entityVersionId, User reviewer, String comment) {
    if (comment == null || comment.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "REVIEW_COMMENT_REQUIRED",
          "A comment explaining what needs to change is required");
    }
    AdminReview review = requirePending(entityType, entityVersionId);
    requireNotSelfReview(review, reviewer);
    review.complete(AdminReviewStatus.CHANGES_REQUESTED, reviewer, comment, clock.instant());
    return review;
  }

  @Transactional
  public AdminReview reject(
      AuditEntityType entityType, UUID entityVersionId, User reviewer, String comment) {
    AdminReview review = requirePending(entityType, entityVersionId);
    requireNotSelfReview(review, reviewer);
    review.complete(AdminReviewStatus.REJECTED, reviewer, comment, clock.instant());
    return review;
  }

  @Transactional(readOnly = true)
  public List<AdminReview> pendingQueue() {
    return adminReviewRepository.findByStatusOrderByCreatedAtAsc(AdminReviewStatus.PENDING);
  }

  @Transactional(readOnly = true)
  public List<AdminReview> history(AuditEntityType entityType, UUID entityVersionId) {
    return adminReviewRepository.findByEntityTypeAndEntityVersionIdOrderByCreatedAtDesc(
        entityType, entityVersionId);
  }

  @Transactional(readOnly = true)
  public Optional<AdminReview> findById(UUID id) {
    return adminReviewRepository.findById(id);
  }

  private AdminReview requirePending(AuditEntityType entityType, UUID entityVersionId) {
    return adminReviewRepository
        .findByEntityTypeAndEntityVersionIdAndStatus(
            entityType, entityVersionId, AdminReviewStatus.PENDING)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.CONFLICT,
                    "NO_PENDING_REVIEW",
                    "This version has no review currently pending"));
  }

  private void requireNotSelfReview(AdminReview review, User reviewer) {
    if (review.getSubmittedBy().getId().equals(reviewer.getId())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SELF_APPROVAL_NOT_ALLOWED",
          "The account that submitted this version for review cannot also review it");
    }
  }
}
