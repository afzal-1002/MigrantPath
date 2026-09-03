package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureRepository;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFactsService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.recommendation.core.Recommendation;
import com.foreignerwarsaw.recommendation.core.RecommendationReason;
import com.foreignerwarsaw.recommendation.core.RecommendationReasonRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRun;
import com.foreignerwarsaw.recommendation.core.RecommendationRunRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRunStatus;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationBundle;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationService;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluator;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recommendation-engine orchestrator (brief §13): loads a completed {@code Assessment}, builds
 * {@code AssessmentFacts}, runs Phase 6's {@link RuleEvaluationService} exactly once, resolves each
 * candidate {@link Procedure}'s active {@link ProcedureVersion}, classifies and ranks every
 * candidate, and persists one immutable {@link RecommendationRun}. Phase 6 remains the sole source
 * of eligibility truth (brief §14) - this class never re-derives a PASS/FAIL/ MISSING/ERROR
 * judgment from {@link AssessmentFacts} itself, only aggregates and ranks what {@link
 * RuleEvaluationService} already decided.
 */
@Service
public class RecommendationService {

  /**
   * Bumped whenever this class's classification/ranking policy changes (brief §21) - independent of
   * {@link RuleEvaluator#ENGINE_VERSION}, which tracks Phase 6's own semantics.
   */
  public static final String ENGINE_VERSION = "1";

  private final AssessmentService assessmentService;
  private final AssessmentFactsService assessmentFactsService;
  private final RuleEvaluationService ruleEvaluationService;
  private final ProcedureRepository procedureRepository;
  private final ProcedureVersionRepository procedureVersionRepository;
  private final RecommendationClassifier classifier;
  private final RecommendationRanker ranker;
  private final RecommendationReasonMapper reasonMapper;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationRepository recommendationRepository;
  private final RecommendationReasonRepository recommendationReasonRepository;
  private final RuleVersionRepository ruleVersionRepository;
  private final Clock clock;

  public RecommendationService(
      AssessmentService assessmentService,
      AssessmentFactsService assessmentFactsService,
      RuleEvaluationService ruleEvaluationService,
      ProcedureRepository procedureRepository,
      ProcedureVersionRepository procedureVersionRepository,
      RecommendationClassifier classifier,
      RecommendationRanker ranker,
      RecommendationReasonMapper reasonMapper,
      RecommendationRunRepository recommendationRunRepository,
      RecommendationRepository recommendationRepository,
      RecommendationReasonRepository recommendationReasonRepository,
      RuleVersionRepository ruleVersionRepository,
      Clock clock) {
    this.assessmentService = assessmentService;
    this.assessmentFactsService = assessmentFactsService;
    this.ruleEvaluationService = ruleEvaluationService;
    this.procedureRepository = procedureRepository;
    this.procedureVersionRepository = procedureVersionRepository;
    this.classifier = classifier;
    this.ranker = ranker;
    this.reasonMapper = reasonMapper;
    this.recommendationRunRepository = recommendationRunRepository;
    this.recommendationRepository = recommendationRepository;
    this.recommendationReasonRepository = recommendationReasonRepository;
    this.ruleVersionRepository = ruleVersionRepository;
    this.clock = clock;
  }

  /**
   * Creates and completes a new immutable {@link RecommendationRun} (brief §39/§70) - never mutates
   * a prior run (brief §37/§62: re-analysis always creates a new one). {@code evaluationDate}
   * defaults to today (via the injected {@link Clock}, brief §59) when {@code null}.
   */
  @Transactional
  public RecommendationRun analyze(UUID assessmentId, User user, LocalDate evaluationDate) {
    Assessment assessment = assessmentService.getOwned(assessmentId, user.getId());
    if (assessment.getStatus() != AssessmentStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ASSESSMENT_NOT_COMPLETED",
          "Recommendations can only be generated for a completed assessment");
    }

    LocalDate resolvedDate = evaluationDate != null ? evaluationDate : LocalDate.now(clock);
    RecommendationRun run =
        recommendationRunRepository.save(
            RecommendationRun.start(
                user,
                assessment,
                resolvedDate,
                ENGINE_VERSION,
                RuleEvaluator.ENGINE_VERSION,
                clock.instant()));

    try {
      List<RankedCandidate> ranked = evaluateAndRank(assessment, resolvedDate);
      persist(run, ranked);
      RecommendationRunStatus status =
          ranked.stream().anyMatch(c -> c.type() == RecommendationType.UNAVAILABLE_FOR_ANALYSIS)
              ? RecommendationRunStatus.PARTIAL
              : RecommendationRunStatus.COMPLETED;
      run.complete(status, clock.instant());
      return run;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      run.complete(RecommendationRunStatus.FAILED, clock.instant());
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "RECOMMENDATION_ANALYSIS_FAILED",
          "Recommendation analysis failed: " + e.getMessage());
    }
  }

  private List<RankedCandidate> evaluateAndRank(Assessment assessment, LocalDate evaluationDate) {
    AssessmentFacts facts = assessmentFactsService.buildFacts(assessment);
    RuleEvaluationBundle bundle =
        ruleEvaluationService.evaluateApplicableRules(facts, evaluationDate);

    List<Candidate> candidates = new ArrayList<>();
    for (Map.Entry<String, List<RuleEvaluationResult>> entry :
        bundle.resultsByTargetCode().entrySet()) {
      List<RuleEvaluationResult> results =
          entry.getValue().stream()
              .filter(r -> r.targetType() == RuleTargetType.PROCEDURE)
              .toList();
      if (results.isEmpty()) {
        continue;
      }
      buildCandidate(entry.getKey(), results, evaluationDate).ifPresent(candidates::add);
    }
    return ranker.rank(candidates);
  }

  private Optional<Candidate> buildCandidate(
      String procedureCode, List<RuleEvaluationResult> results, LocalDate evaluationDate) {
    Optional<Procedure> procedureOpt = procedureRepository.findByCodeIgnoreCase(procedureCode);
    if (procedureOpt.isEmpty()) {
      // A Rule targets a procedure code with no matching Procedure identity - a content
      // gap, never silently hidden nor crashed on; skip it from candidates entirely
      // since there is no Procedure row to attach a Recommendation to (brief §115's
      // spirit extended one level further).
      return Optional.empty();
    }
    Procedure procedure = procedureOpt.get();
    Optional<ProcedureVersion> versionOpt =
        procedureVersionRepository.findActivePublishedVersion(procedure.getId(), evaluationDate);
    if (versionOpt.isEmpty()) {
      // Brief §28: a Rule may indicate a route exists, but with no active PUBLISHED
      // content to show, never present confident guidance.
      return Optional.of(
          new Candidate(procedure, null, RecommendationType.UNAVAILABLE_FOR_ANALYSIS, results));
    }
    RecommendationType type = classifier.classify(results);
    return Optional.of(new Candidate(procedure, versionOpt.get(), type, results));
  }

  private void persist(RecommendationRun run, List<RankedCandidate> ranked) {
    for (RankedCandidate candidate : ranked) {
      Recommendation recommendation =
          recommendationRepository.save(
              new Recommendation(
                  run,
                  candidate.procedure(),
                  candidate.procedureVersion(),
                  candidate.type(),
                  candidate.rank(),
                  clock.instant()));

      List<ReasonDraft> drafts = reasonMapper.mapReasons(candidate.ruleResults());
      int order = 0;
      for (ReasonDraft draft : drafts) {
        // getReferenceById - a lazy proxy, not a real fetch - the FK is all a
        // RecommendationReason row needs (brief §60's provenance chain), never the
        // full RuleVersion loaded just to save a reference to it.
        RuleVersion ruleVersion =
            draft.ruleVersionId() != null
                ? ruleVersionRepository.getReferenceById(draft.ruleVersionId())
                : null;
        recommendationReasonRepository.save(
            new RecommendationReason(
                recommendation,
                draft.type(),
                draft.reasonCode(),
                ruleVersion,
                draft.conditionCode(),
                draft.factCode(),
                draft.messageKey(),
                order++));
      }
    }
  }
}
