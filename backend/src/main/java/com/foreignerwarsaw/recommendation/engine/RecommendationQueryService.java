package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import com.foreignerwarsaw.recommendation.core.Recommendation;
import com.foreignerwarsaw.recommendation.core.RecommendationReason;
import com.foreignerwarsaw.recommendation.core.RecommendationReasonRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationReasonType;
import com.foreignerwarsaw.recommendation.core.RecommendationRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRun;
import com.foreignerwarsaw.recommendation.core.RecommendationRunRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationReasonResponse;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationResponse;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationRunResponse;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationRunSummaryResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side assembly of {@code RecommendationRun} detail/summary responses (brief §82) - never
 * mutates, never re-evaluates (brief §61: a stored run is returned exactly as computed, not
 * recomputed on read).
 */
@Service
public class RecommendationQueryService {

  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationRepository recommendationRepository;
  private final RecommendationReasonRepository recommendationReasonRepository;
  private final RecommendationSourceResolver sourceResolver;

  public RecommendationQueryService(
      RecommendationRunRepository recommendationRunRepository,
      RecommendationRepository recommendationRepository,
      RecommendationReasonRepository recommendationReasonRepository,
      RecommendationSourceResolver sourceResolver) {
    this.recommendationRunRepository = recommendationRunRepository;
    this.recommendationRepository = recommendationRepository;
    this.recommendationReasonRepository = recommendationReasonRepository;
    this.sourceResolver = sourceResolver;
  }

  /**
   * {@code requestingUserId} enforces ownership here directly ({@link RecommendationRun} isn't
   * assessment-path-scoped like the other two read methods below) - a 404, never a 403, for another
   * user's run, same IDOR discipline as every other owned resource in this codebase (brief
   * §84/§105).
   */
  @Transactional(readOnly = true)
  public RecommendationRunResponse getRunDetail(UUID runId, UUID requestingUserId) {
    RecommendationRun run =
        recommendationRunRepository
            .findByIdFetchingAssessmentAndUser(runId)
            .filter(r -> r.getUser().getId().equals(requestingUserId))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RECOMMENDATION_RUN_NOT_FOUND",
                        "No recommendation run found for id " + runId));
    return toDetailResponse(run);
  }

  @Transactional(readOnly = true)
  public RecommendationRunResponse getLatestForAssessment(UUID assessmentId) {
    RecommendationRun run =
        recommendationRunRepository
            .findFirstByAssessment_IdOrderByCreatedAtDesc(assessmentId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RECOMMENDATION_RUN_NOT_FOUND",
                        "No recommendation run exists yet for this assessment"));
    return toDetailResponse(run);
  }

  @Transactional(readOnly = true)
  public List<RecommendationRunSummaryResponse> getHistory(UUID assessmentId) {
    List<RecommendationRun> runs =
        recommendationRunRepository.findByAssessment_IdOrderByCreatedAtDesc(assessmentId);
    return runs.stream().map(this::toSummaryResponse).toList();
  }

  private RecommendationRunSummaryResponse toSummaryResponse(RecommendationRun run) {
    List<Recommendation> recommendations =
        recommendationRepository.findByRecommendationRun_IdOrderByRankAsc(run.getId());
    long primaryMatchCount =
        recommendations.stream()
            .filter(r -> r.getRecommendationType() == RecommendationType.PRIMARY_MATCH)
            .count();
    return new RecommendationRunSummaryResponse(
        run.getId(),
        run.getEvaluationDate(),
        run.getStatus().name(),
        run.getCreatedAt(),
        run.getCompletedAt(),
        recommendations.size(),
        (int) primaryMatchCount);
  }

  private RecommendationRunResponse toDetailResponse(RecommendationRun run) {
    List<Recommendation> recommendations =
        recommendationRepository.findByRecommendationRun_IdOrderByRankAsc(run.getId());
    List<RecommendationReason> allReasons =
        recommendationReasonRepository
            .findByRecommendation_RecommendationRun_IdOrderByRecommendation_IdAscDisplayOrderAsc(
                run.getId());

    List<RecommendationResponse> recommendationResponses =
        recommendations.stream().map(rec -> toResponse(rec, reasonsFor(rec, allReasons))).toList();

    return new RecommendationRunResponse(
        run.getId(),
        run.getAssessment().getId(),
        run.getEvaluationDate(),
        run.getStatus().name(),
        run.getRecommendationEngineVersion(),
        run.getRuleEngineVersion(),
        run.getCreatedAt(),
        run.getCompletedAt(),
        recommendationResponses);
  }

  private List<RecommendationReason> reasonsFor(
      Recommendation recommendation, List<RecommendationReason> all) {
    return all.stream()
        .filter(r -> r.getRecommendation().getId().equals(recommendation.getId()))
        .toList();
  }

  private RecommendationResponse toResponse(
      Recommendation recommendation, List<RecommendationReason> reasons) {
    ProcedureVersion version = recommendation.getProcedureVersion();
    String title =
        version != null ? version.getTitle() : recommendation.getProcedure().getCanonicalName();

    List<RecommendationReasonResponse> reasonResponses =
        reasons.stream().map(RecommendationReasonResponse::from).toList();
    List<String> missingFacts =
        reasons.stream()
            .filter(r -> r.getReasonType() == RecommendationReasonType.MISSING_INFORMATION)
            .map(RecommendationReason::getFactCode)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    Set<UUID> ruleVersionIds =
        reasons.stream()
            .map(RecommendationReason::getRuleVersion)
            .filter(java.util.Objects::nonNull)
            .map(rv -> rv.getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<SourceResponse> sources =
        sourceResolver.resolve(version != null ? version.getId() : null, ruleVersionIds).stream()
            .map(rs -> SourceResponse.from(rs.source(), rs.role().name()))
            .toList();

    return new RecommendationResponse(
        recommendation.getId(),
        recommendation.getProcedure().getCode(),
        title,
        recommendation.getRecommendationType().name(),
        recommendation.getRank(),
        reasonResponses,
        missingFacts,
        sources);
  }
}
