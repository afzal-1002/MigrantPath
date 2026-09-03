package com.foreignerwarsaw.admin.review;

import com.foreignerwarsaw.admin.dto.AdminReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's cross-entity review queue (brief §65) - every version currently {@code PENDING} review,
 * across Procedure/Rule/Threshold/Questionnaire alike, in one place rather than four separate
 * per-domain lists a reviewer would otherwise have to check.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@Tag(name = "Admin - Reviews")
public class AdminReviewQueueController {

  private final ContentReviewCoordinator reviewCoordinator;

  public AdminReviewQueueController(ContentReviewCoordinator reviewCoordinator) {
    this.reviewCoordinator = reviewCoordinator;
  }

  @Operation(summary = "Every version currently pending review, across all content types")
  @GetMapping
  public List<AdminReviewResponse> pendingQueue() {
    return reviewCoordinator.pendingQueue().stream().map(AdminReviewResponse::from).toList();
  }
}
