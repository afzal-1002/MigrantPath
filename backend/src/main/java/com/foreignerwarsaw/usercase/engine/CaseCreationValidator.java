package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.procedure.step.StepVersionRepository;
import com.foreignerwarsaw.recommendation.core.Recommendation;
import com.foreignerwarsaw.recommendation.core.RecommendationRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every gate a candidate {@link Recommendation} must clear before a {@link
 * com.foreignerwarsaw.usercase.core.UserCase} may be created from it (brief §4/§5/§90/§91) - kept
 * as one focused, testable class (brief §91's own suggested name) rather than scattered across the
 * creation service.
 */
@Service
public class CaseCreationValidator {

  /**
   * Brief §4: safer MVP - MORE_INFORMATION_REQUIRED, NOT_APPLICABLE, and UNAVAILABLE_FOR_ANALYSIS
   * never permit case creation; only a genuinely determined match does.
   */
  private static final Set<RecommendationType> CASE_CREATABLE_TYPES =
      Set.of(RecommendationType.PRIMARY_MATCH, RecommendationType.POSSIBLE_ALTERNATIVE);

  private final RecommendationRepository recommendationRepository;
  private final ProcedureVersionRepository procedureVersionRepository;
  private final StepVersionRepository stepVersionRepository;
  private final Clock clock;

  public CaseCreationValidator(
      RecommendationRepository recommendationRepository,
      ProcedureVersionRepository procedureVersionRepository,
      StepVersionRepository stepVersionRepository,
      Clock clock) {
    this.recommendationRepository = recommendationRepository;
    this.procedureVersionRepository = procedureVersionRepository;
    this.stepVersionRepository = stepVersionRepository;
    this.clock = clock;
  }

  /**
   * @return the validated {@link Recommendation} together with the current active {@link
   *     ProcedureVersion} it must be created against (identical to the recommendation's own pinned
   *     version - brief §5's "if newer applicable versions exist, RECOMMENDATION_OUTDATED").
   */
  @Transactional(readOnly = true)
  public ValidatedRecommendation validate(UUID recommendationId, UUID requestingUserId) {
    Recommendation recommendation =
        recommendationRepository
            .findByIdFetchingRunAndProcedure(recommendationId)
            .filter(r -> r.getRecommendationRun().getUser().getId().equals(requestingUserId))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RECOMMENDATION_NOT_FOUND",
                        "No recommendation found for id " + recommendationId));

    if (!CASE_CREATABLE_TYPES.contains(recommendation.getRecommendationType())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_CREATION_NOT_ALLOWED",
          "A case cannot be created from a %s recommendation"
              .formatted(recommendation.getRecommendationType()));
    }

    LocalDate today = LocalDate.now(clock);
    ProcedureVersion currentActive =
        procedureVersionRepository
            .findActivePublishedVersion(recommendation.getProcedure().getId(), today)
            .orElse(null);
    boolean stillCurrent =
        currentActive != null
            && recommendation.getProcedureVersion() != null
            && currentActive.getId().equals(recommendation.getProcedureVersion().getId());
    if (!stillCurrent) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "RECOMMENDATION_OUTDATED",
          "A newer analysis is available - run analysis again before starting this case");
    }

    if (stepVersionRepository
        .findByProcedureVersion_IdOrderBySortOrderAsc(currentActive.getId())
        .isEmpty()) {
      // Brief §90: never create a case with an empty checklist just because the recommendation
      // itself was valid.
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_CONTENT_NOT_READY",
          "This procedure's content is not yet ready to track as a case");
    }

    return new ValidatedRecommendation(recommendation, currentActive, today);
  }

  public record ValidatedRecommendation(
      Recommendation recommendation, ProcedureVersion procedureVersion, LocalDate evaluationDate) {}
}
