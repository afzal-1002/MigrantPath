package com.foreignerwarsaw.dashboard;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.dashboard.dto.DashboardActivityResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardAssessmentStatusResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardCaseResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardDateResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardMilestoneResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardNextActionResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardProfileResponse;
import com.foreignerwarsaw.dashboard.dto.DashboardResponse;
import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentCompletionService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentRepository;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.recommendation.engine.RecommendationQueryService;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationResponse;
import com.foreignerwarsaw.recommendation.engine.dto.RecommendationRunResponse;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import com.foreignerwarsaw.usercase.core.UserCaseStatus;
import com.foreignerwarsaw.usercase.engine.UserCaseQueryService;
import com.foreignerwarsaw.usercase.engine.dto.CaseDetailResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseSummaryResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - a read-only aggregation service, composing existing
 * domain read services (brief §32/§67/§68). No Rules/Recommendation/UserCase state-machine logic
 * lives here - only summarization and a documented, deterministic "what's most relevant right now"
 * selection (brief §30/§31), never a random or arbitrary choice.
 */
@Service
public class DashboardService {

  /**
   * Same definition this codebase's own frontend already used pre-redesign - "active" means "not a
   * terminal outcome," not "currently being processed by an authority."
   */
  private static final Set<UserCaseStatus> ACTIVE_STATUSES =
      EnumSet.complementOf(EnumSet.of(UserCaseStatus.COMPLETED, UserCaseStatus.CANCELLED));

  private static final int MAX_NEXT_ACTIONS = 4;
  private static final int MAX_ACTIVITY = 8;
  private static final int MAX_MILESTONES = 8;
  private static final int MAX_ACTIVE_CASES = 4;
  private static final int MAX_RECOMMENDATIONS = 3;

  private final UserAccountService userAccountService;
  private final UserCaseRepository userCaseRepository;
  private final UserCaseQueryService userCaseQueryService;
  private final UserCaseEventRepository userCaseEventRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentCompletionService assessmentCompletionService;
  private final RecommendationQueryService recommendationQueryService;

  public DashboardService(
      UserAccountService userAccountService,
      UserCaseRepository userCaseRepository,
      UserCaseQueryService userCaseQueryService,
      UserCaseEventRepository userCaseEventRepository,
      AssessmentRepository assessmentRepository,
      AssessmentCompletionService assessmentCompletionService,
      RecommendationQueryService recommendationQueryService) {
    this.userAccountService = userAccountService;
    this.userCaseRepository = userCaseRepository;
    this.userCaseQueryService = userCaseQueryService;
    this.userCaseEventRepository = userCaseEventRepository;
    this.assessmentRepository = assessmentRepository;
    this.assessmentCompletionService = assessmentCompletionService;
    this.recommendationQueryService = recommendationQueryService;
  }

  @Transactional(readOnly = true)
  public DashboardResponse getDashboard(UUID userId) {
    User user = userAccountService.getById(userId);
    List<UserCase> allCases = userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(userId);
    List<UserCase> activeCaseEntities =
        allCases.stream().filter(c -> ACTIVE_STATUSES.contains(c.getStatus())).toList();

    UserCase primary = selectPrimaryCase(activeCaseEntities);
    CaseDetailResponse primaryDetail =
        primary == null ? null : userCaseQueryService.getDetail(primary.getId(), userId);

    List<Assessment> assessments = assessmentRepository.findByUser_IdOrderByStartedAtDesc(userId);
    Assessment relevantAssessment = selectRelevantAssessment(assessments);
    DashboardAssessmentStatusResponse assessmentStatus =
        relevantAssessment == null
            ? null
            : new DashboardAssessmentStatusResponse(
                relevantAssessment.getId(),
                relevantAssessment.getStatus().name(),
                assessmentCompletionService.progressPercent(relevantAssessment));

    List<RecommendationResponse> latestRecommendations =
        activeCaseEntities.isEmpty() ? loadRecommendations(relevantAssessment) : List.of();

    List<CaseSummaryResponse> activeCaseSummaries =
        userCaseQueryService.listForUser(userId).stream()
            .filter(c -> ACTIVE_STATUSES.contains(UserCaseStatus.valueOf(c.status())))
            .limit(MAX_ACTIVE_CASES)
            .toList();

    List<UserCaseEvent> primaryEvents =
        primary == null
            ? List.of()
            : userCaseEventRepository.findByUserCase_IdOrderByOccurredAtDesc(primary.getId());

    return new DashboardResponse(
        new DashboardProfileResponse(
            user.getFirstName() != null ? user.getFirstName() : user.getEmail(),
            user.getEmail(),
            user.isEmailVerified(),
            "Warsaw"),
        primaryDetail == null ? null : toDashboardCase(primaryDetail),
        activeCaseSummaries,
        buildNextActions(
            primaryDetail, relevantAssessment, assessmentStatus, latestRecommendations),
        buildImportantDates(primaryDetail),
        buildMilestones(primaryEvents, relevantAssessment, latestRecommendations),
        latestRecommendations.stream().limit(MAX_RECOMMENDATIONS).toList(),
        buildRecentActivity(primaryEvents),
        assessmentStatus);
  }

  /**
   * brief §30 - documented priority: a case flagged with a changed requirement first, else the most
   * recently updated active case (the repository already orders by updatedAt DESC), else null if
   * there are none. Never random.
   */
  private UserCase selectPrimaryCase(List<UserCase> activeCases) {
    if (activeCases.isEmpty()) {
      return null;
    }
    return activeCases.stream()
        .filter(this::hasRequirementUpdates)
        .findFirst()
        .orElse(activeCases.get(0));
  }

  private boolean hasRequirementUpdates(UserCase userCase) {
    // Reuses the same detection UserCaseQueryService/CaseRequirementChangeService already
    // expose per-case; cheap enough here since it's only evaluated while selecting the
    // primary case among an already-small "this user's active cases" list, never per every
    // case in the system.
    return userCaseQueryService
        .getDetail(userCase.getId(), userCase.getUser().getId())
        .hasRequirementUpdates();
  }

  private Assessment selectRelevantAssessment(List<Assessment> assessments) {
    return assessments.stream()
        .filter(a -> a.getStatus() == AssessmentStatus.IN_PROGRESS)
        .findFirst()
        .or(
            () ->
                assessments.stream()
                    .filter(a -> a.getStatus() == AssessmentStatus.COMPLETED)
                    .findFirst())
        .orElse(null);
  }

  private List<RecommendationResponse> loadRecommendations(Assessment assessment) {
    if (assessment == null || assessment.getStatus() != AssessmentStatus.COMPLETED) {
      return List.of();
    }
    try {
      RecommendationRunResponse run =
          recommendationQueryService.getLatestForAssessment(assessment.getId());
      return run.recommendations().stream()
          .filter(
              r ->
                  r.recommendationType().equals(RecommendationType.PRIMARY_MATCH.name())
                      || r.recommendationType()
                          .equals(RecommendationType.POSSIBLE_ALTERNATIVE.name()))
          .toList();
    } catch (ApiException e) {
      // No run exists yet for this assessment - a real, expected state (brief §14's own
      // "expected 4xx" framing extends here), not an error to surface on the dashboard.
      return List.of();
    }
  }

  /**
   * brief §12/§13 - a real, 5-stage mapping of the actual {@link UserCaseStatus} state machine,
   * never implying a submission the case's own status doesn't support.
   */
  private int stageIndex(UserCaseStatus status) {
    return switch (status) {
      case DRAFT, PREPARING -> 0;
      case READY_TO_SUBMIT -> 1;
      case SUBMITTED, WAITING, ADDITIONAL_DOCUMENTS_REQUIRED -> 2;
      case DECISION_RECEIVED -> 3;
      case APPROVED, REJECTED, APPEAL, COMPLETED -> 4;
      case CANCELLED -> 0;
    };
  }

  private static final List<String> STAGE_LABELS =
      List.of("Started", "Ready to submit", "Submitted", "Decision pending", "Decision received");

  private DashboardCaseResponse toDashboardCase(CaseDetailResponse detail) {
    long daysActive = Duration.between(detail.createdAt(), Instant.now()).toDays();
    UserCaseStatus status = UserCaseStatus.valueOf(detail.status());
    long feesCompleted =
        detail.fees().stream()
            .filter(f -> "PAID".equals(f.status()) || "NOT_APPLICABLE".equals(f.status()))
            .count();
    return new DashboardCaseResponse(
        detail.id(),
        detail.procedureCode(),
        detail.procedureTitle(),
        detail.status(),
        detail.createdAt(),
        Math.max(daysActive, 0),
        detail.progress().stepsCompleted(),
        detail.progress().stepsTotal(),
        detail.progress().documentsReady(),
        detail.progress().documentsTotal(),
        (int) feesCompleted,
        detail.fees().size(),
        detail.hasRequirementUpdates(),
        stageIndex(status),
        STAGE_LABELS.get(stageIndex(status)),
        detail.authorities(),
        detail.offices());
  }

  /**
   * brief §31 - documented, deterministic priority (adapted to this codebase's real statuses): a
   * requirement-changed case first, then an incomplete checklist item, then a ready-but-unstarted
   * recommendation, then an in-progress assessment, then "start an assessment." Never more than
   * {@link #MAX_NEXT_ACTIONS}.
   */
  private List<DashboardNextActionResponse> buildNextActions(
      CaseDetailResponse primaryCase,
      Assessment relevantAssessment,
      DashboardAssessmentStatusResponse assessmentStatus,
      List<RecommendationResponse> recommendations) {
    List<DashboardNextActionResponse> actions = new ArrayList<>();

    if (primaryCase != null && primaryCase.hasRequirementUpdates()) {
      actions.add(
          new DashboardNextActionResponse(
              "Requirements have changed",
              primaryCase.procedureTitle() + " has an updated requirement to review.",
              "ATTENTION",
              "Review changes",
              primaryCase.id(),
              null));
    }
    if (primaryCase != null
        && (primaryCase.progress().stepsCompleted() < primaryCase.progress().stepsTotal()
            || primaryCase.progress().documentsReady() < primaryCase.progress().documentsTotal())) {
      actions.add(
          new DashboardNextActionResponse(
              "Continue your checklist",
              primaryCase.procedureTitle()
                  + ": "
                  + primaryCase.progress().stepsCompleted()
                  + "/"
                  + primaryCase.progress().stepsTotal()
                  + " steps, "
                  + primaryCase.progress().documentsReady()
                  + "/"
                  + primaryCase.progress().documentsTotal()
                  + " documents ready.",
              "NEXT",
              "Open checklist",
              primaryCase.id(),
              null));
    }
    if (primaryCase == null && !recommendations.isEmpty() && relevantAssessment != null) {
      RecommendationResponse primary =
          recommendations.stream()
              .filter(r -> r.recommendationType().equals(RecommendationType.PRIMARY_MATCH.name()))
              .findFirst()
              .orElse(recommendations.get(0));
      actions.add(
          new DashboardNextActionResponse(
              "Your recommended pathway is ready",
              primary.procedureTitle() + " appears relevant based on your answers.",
              "NEXT",
              "View recommendations",
              null,
              relevantAssessment.getId()));
    }
    if (assessmentStatus != null && "IN_PROGRESS".equals(assessmentStatus.status())) {
      actions.add(
          new DashboardNextActionResponse(
              "Continue your assessment",
              assessmentStatus.progressPercent() + "% of the visible questions answered.",
              "NEXT",
              "Continue",
              null,
              assessmentStatus.assessmentId()));
    }
    if (assessmentStatus == null) {
      actions.add(
          new DashboardNextActionResponse(
              "Find the right pathway for you",
              "Answer a few questions to see which procedures may be relevant to your situation.",
              "INFO",
              "Start assessment",
              null,
              null));
    }

    return actions.stream().limit(MAX_NEXT_ACTIONS).toList();
  }

  /**
   * brief §16/§20/§55 - only dates already recorded on the case itself, never a computed or
   * estimated immigration deadline.
   */
  private List<DashboardDateResponse> buildImportantDates(CaseDetailResponse primaryCase) {
    if (primaryCase == null) {
      return List.of();
    }
    List<DashboardDateResponse> dates = new ArrayList<>();
    dates.add(new DashboardDateResponse("Case started", primaryCase.createdAt(), "CASE_STARTED"));
    if (primaryCase.submittedAt() != null) {
      dates.add(
          new DashboardDateResponse("Case submitted", primaryCase.submittedAt(), "CASE_SUBMITTED"));
    }
    if (primaryCase.completedAt() != null) {
      dates.add(
          new DashboardDateResponse("Case completed", primaryCase.completedAt(), "CASE_COMPLETED"));
    }
    return dates;
  }

  private static final Set<String> MILESTONE_EVENT_TYPES =
      Set.of("CASE_CREATED", "STEP_COMPLETED", "FEE_STATUS_CHANGED");

  private List<DashboardMilestoneResponse> buildMilestones(
      List<UserCaseEvent> events,
      Assessment relevantAssessment,
      List<RecommendationResponse> recommendations) {
    List<DashboardMilestoneResponse> milestones = new ArrayList<>();
    if (relevantAssessment != null
        && relevantAssessment.getStatus() == AssessmentStatus.COMPLETED) {
      milestones.add(
          new DashboardMilestoneResponse(
              "Assessment completed", relevantAssessment.getCompletedAt()));
    }
    if (!recommendations.isEmpty() && relevantAssessment != null) {
      milestones.add(
          new DashboardMilestoneResponse(
              "Recommendation generated", relevantAssessment.getCompletedAt()));
    }
    events.stream()
        .filter(e -> MILESTONE_EVENT_TYPES.contains(e.getEventType().name()))
        .map(e -> new DashboardMilestoneResponse(friendlyEventLabel(e), e.getOccurredAt()))
        .forEach(milestones::add);

    return milestones.stream()
        .sorted(Comparator.comparing(DashboardMilestoneResponse::occurredAt).reversed())
        .limit(MAX_MILESTONES)
        .toList();
  }

  private List<DashboardActivityResponse> buildRecentActivity(List<UserCaseEvent> events) {
    return events.stream()
        .limit(MAX_ACTIVITY)
        .map(e -> new DashboardActivityResponse(friendlyEventLabel(e), e.getOccurredAt()))
        .toList();
  }

  /**
   * brief §22/§44/§50 - a single, centralized mapping from a real event type to user-friendly
   * wording; never a raw enum name.
   */
  private String friendlyEventLabel(UserCaseEvent event) {
    return switch (event.getEventType()) {
      case CASE_CREATED -> "Case created";
      case CASE_STATUS_CHANGED -> "Case status updated";
      case STEP_COMPLETED -> "Checklist step completed";
      case STEP_REOPENED -> "Checklist step reopened";
      case DOCUMENT_STATUS_CHANGED -> "Document checklist updated";
      case FEE_STATUS_CHANGED -> "Fee status updated";
      case REQUIREMENTS_UPDATE_DETECTED -> "A requirement changed";
      case CASE_UPDATED_TO_NEW_VERSION -> "Case updated to the latest requirements";
      case CASE_CANCELLED -> "Case cancelled";
    };
  }
}
