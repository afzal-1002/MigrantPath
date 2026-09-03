package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.questionnaire.assessment.AssessmentService;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationRunResponse;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationRunSummaryResponse;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint requires an authenticated session and enforces ownership (brief §84/§105) - the
 * two assessment-scoped routes reuse {@link AssessmentService#getOwned} exactly like every other
 * {@code /api/v1/assessments/{id}/...} endpoint (a 404, never a 403, for another user's
 * assessment); {@code /recommendation-runs/{id}} enforces ownership inside {@link
 * RecommendationQueryService#getRunDetail} since it isn't assessment-path-scoped.
 */
@RestController
@Tag(name = "Recommendations")
public class RecommendationController {

  private final AssessmentService assessmentService;
  private final RecommendationService recommendationService;
  private final RecommendationQueryService recommendationQueryService;
  private final UserAccountService userAccountService;

  public RecommendationController(
      AssessmentService assessmentService,
      RecommendationService recommendationService,
      RecommendationQueryService recommendationQueryService,
      UserAccountService userAccountService) {
    this.assessmentService = assessmentService;
    this.recommendationService = recommendationService;
    this.recommendationQueryService = recommendationQueryService;
    this.userAccountService = userAccountService;
  }

  @Operation(
      summary =
          "Analyse a completed assessment (brief §39): creates a new immutable RecommendationRun,"
              + " never overwrites a prior one")
  @PostMapping("/api/v1/assessments/{id}/recommendation-runs")
  public RecommendationRunResponse analyze(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate evaluationDate,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    var run =
        recommendationService.analyze(
            id, userAccountService.getById(principal.getUserId()), evaluationDate);
    return recommendationQueryService.getRunDetail(run.getId(), principal.getUserId());
  }

  @Operation(summary = "The most recent RecommendationRun for this assessment (brief §79)")
  @GetMapping("/api/v1/assessments/{id}/recommendations/latest")
  public RecommendationRunResponse latest(
      @PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
    assessmentService.getOwned(id, principal.getUserId());
    return recommendationQueryService.getLatestForAssessment(id);
  }

  @Operation(summary = "Every RecommendationRun for this assessment, most recent first (brief §81)")
  @GetMapping("/api/v1/assessments/{id}/recommendation-runs")
  public List<RecommendationRunSummaryResponse> history(
      @PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
    assessmentService.getOwned(id, principal.getUserId());
    return recommendationQueryService.getHistory(id);
  }

  @Operation(summary = "One specific RecommendationRun by id (brief §80)")
  @GetMapping("/api/v1/recommendation-runs/{runId}")
  public RecommendationRunResponse get(
      @PathVariable UUID runId, @AuthenticationPrincipal AppUserPrincipal principal) {
    return recommendationQueryService.getRunDetail(runId, principal.getUserId());
  }
}
