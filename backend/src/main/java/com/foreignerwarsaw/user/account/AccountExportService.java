package com.foreignerwarsaw.user.account;

import com.foreignerwarsaw.auth.UserConsent;
import com.foreignerwarsaw.auth.UserConsentRepository;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.observability.PrivacyMetrics;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentAnswer;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentAnswerRepository;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRepository;
import com.foreignerwarsaw.recommendation.core.RecommendationRunRepository;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import com.foreignerwarsaw.user.account.dto.AccountExportResponse;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical Phase 12 personal-data export (brief §16-§25). Builds {@link AccountExportResponse}
 * entirely from explicit DTO mapping - never {@code entity -> JSON} directly. Read-only,
 * single-transaction snapshot (brief §107: "current MVP data volume is small enough that a
 * transaction/read-consistency-level snapshot is acceptable, no serializable isolation needed").
 */
@Service
public class AccountExportService {

  private final UserRepository userRepository;
  private final UserConsentRepository userConsentRepository;
  private final AssessmentRepository assessmentRepository;
  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final RecommendationRunRepository recommendationRunRepository;
  private final RecommendationRepository recommendationRepository;
  private final UserCaseRepository userCaseRepository;
  private final UserCaseStepRepository userCaseStepRepository;
  private final UserCaseDocumentRepository userCaseDocumentRepository;
  private final UserCaseFeeRepository userCaseFeeRepository;
  private final UserCaseEventRepository userCaseEventRepository;
  private final AuditService auditService;
  private final SecurityEventLogger securityEventLogger;
  private final PrivacyMetrics privacyMetrics;
  private final Clock clock;

  public AccountExportService(
      UserRepository userRepository,
      UserConsentRepository userConsentRepository,
      AssessmentRepository assessmentRepository,
      AssessmentAnswerRepository assessmentAnswerRepository,
      RecommendationRunRepository recommendationRunRepository,
      RecommendationRepository recommendationRepository,
      UserCaseRepository userCaseRepository,
      UserCaseStepRepository userCaseStepRepository,
      UserCaseDocumentRepository userCaseDocumentRepository,
      UserCaseFeeRepository userCaseFeeRepository,
      UserCaseEventRepository userCaseEventRepository,
      AuditService auditService,
      SecurityEventLogger securityEventLogger,
      PrivacyMetrics privacyMetrics,
      Clock clock) {
    this.userRepository = userRepository;
    this.userConsentRepository = userConsentRepository;
    this.assessmentRepository = assessmentRepository;
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.recommendationRunRepository = recommendationRunRepository;
    this.recommendationRepository = recommendationRepository;
    this.userCaseRepository = userCaseRepository;
    this.userCaseStepRepository = userCaseStepRepository;
    this.userCaseDocumentRepository = userCaseDocumentRepository;
    this.userCaseFeeRepository = userCaseFeeRepository;
    this.userCaseEventRepository = userCaseEventRepository;
    this.auditService = auditService;
    this.securityEventLogger = securityEventLogger;
    this.privacyMetrics = privacyMetrics;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AccountExportResponse exportOwnData(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new IllegalStateException("Authenticated principal has no matching user row"));

    auditService.record(
        user,
        AuditActionType.PERSONAL_DATA_EXPORT_REQUESTED,
        AuditEntityType.USER,
        userId,
        null,
        null,
        "Personal data export requested");

    AccountExportResponse response;
    try {
      response =
          new AccountExportResponse(
              AccountExportResponse.SCHEMA_VERSION,
              clock.instant(),
              userId,
              mapAccount(user),
              mapConsents(userId),
              mapAssessments(userId),
              mapRecommendationRuns(userId),
              mapCases(userId));
    } catch (RuntimeException e) {
      // Canonical Phase 14 (Observability) brief §79 - the request audit row above
      // already recorded the attempt; this counts the unexpected-failure signal a
      // human operator would need without ever touching the payload itself.
      privacyMetrics.recordExportFailed();
      throw e;
    }

    auditService.record(
        user,
        AuditActionType.PERSONAL_DATA_EXPORT_COMPLETED,
        AuditEntityType.USER,
        userId,
        null,
        null,
        "Personal data export completed");
    securityEventLogger.log(SecurityEventLogger.Event.PERSONAL_DATA_EXPORTED, userId.toString());
    privacyMetrics.recordExportCompleted();

    return response;
  }

  private AccountExportResponse.Account mapAccount(User user) {
    return new AccountExportResponse.Account(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getPreferredLanguage(),
        user.isEmailVerified(),
        user.getRoles().stream().map(Role::getCode).sorted().toList(),
        user.getCreatedAt());
  }

  private List<AccountExportResponse.Consent> mapConsents(UUID userId) {
    return userConsentRepository.findByUser_IdOrderByAcceptedAtAsc(userId).stream()
        .map(
            (UserConsent c) ->
                new AccountExportResponse.Consent(
                    c.getConsentType().name(), c.getPolicyVersion(), c.getAcceptedAt()))
        .toList();
  }

  private List<AccountExportResponse.Assessment> mapAssessments(UUID userId) {
    return assessmentRepository.findByUser_IdOrderByStartedAtDesc(userId).stream()
        .map(
            a -> {
              List<AccountExportResponse.Answer> answers =
                  assessmentAnswerRepository.findByAssessment_Id(a.getId()).stream()
                      .filter(AssessmentAnswer::isApplicable)
                      .map(
                          ans ->
                              new AccountExportResponse.Answer(
                                  ans.getQuestion().getCode(), ans.logicalValue(), ans.isUnsure()))
                      .toList();
              return new AccountExportResponse.Assessment(
                  a.getId(),
                  a.getQuestionnaire().getCode(),
                  a.getQuestionnaireVersion().getVersionNumber(),
                  a.getStatus().name(),
                  a.getStartedAt(),
                  a.getCompletedAt(),
                  answers);
            })
        .toList();
  }

  private List<AccountExportResponse.RecommendationRun> mapRecommendationRuns(UUID userId) {
    return recommendationRunRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
        .map(
            run -> {
              List<AccountExportResponse.Recommendation> recs =
                  recommendationRepository
                      .findByRecommendationRun_IdOrderByRankAsc(run.getId())
                      .stream()
                      .map(
                          r ->
                              new AccountExportResponse.Recommendation(
                                  r.getId(),
                                  r.getProcedure().getCode(),
                                  r.getRecommendationType().name(),
                                  r.getRank(),
                                  r.getCreatedAt()))
                      .toList();
              return new AccountExportResponse.RecommendationRun(
                  run.getId(),
                  run.getAssessment().getId(),
                  run.getEvaluationDate(),
                  run.getStatus().name(),
                  run.getCreatedAt(),
                  recs);
            })
        .toList();
  }

  private List<AccountExportResponse.Case> mapCases(UUID userId) {
    return userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(userId).stream()
        .map(this::mapCase)
        .toList();
  }

  private AccountExportResponse.Case mapCase(UserCase userCase) {
    UUID revisionId =
        userCase.getCurrentRevision() != null ? userCase.getCurrentRevision().getId() : null;

    List<AccountExportResponse.Step> steps =
        revisionId == null
            ? List.of()
            : userCaseStepRepository
                .findBySnapshotRevision_IdOrderBySortOrderAsc(revisionId)
                .stream()
                .map(
                    s ->
                        new AccountExportResponse.Step(
                            s.getStableCode(),
                            s.getTitleSnapshot(),
                            s.getStatus().name(),
                            s.isMandatory()))
                .toList();

    List<AccountExportResponse.Document> documents =
        revisionId == null
            ? List.of()
            : userCaseDocumentRepository
                .findBySnapshotRevision_IdOrderBySortOrderAsc(revisionId)
                .stream()
                .map(
                    d ->
                        new AccountExportResponse.Document(
                            d.getStableCode(),
                            d.getNameSnapshot(),
                            d.getStatus().name(),
                            d.isMandatory(),
                            d.getUserNote()))
                .toList();

    List<AccountExportResponse.Fee> fees =
        revisionId == null
            ? List.of()
            : userCaseFeeRepository
                .findBySnapshotRevision_IdOrderBySortOrderAsc(revisionId)
                .stream()
                .map(
                    f ->
                        new AccountExportResponse.Fee(
                            f.getStableCode(),
                            f.getFeeType().name(),
                            f.getAmountSnapshot(),
                            f.getCurrencySnapshot(),
                            f.getStatus().name()))
                .toList();

    List<AccountExportResponse.Event> events =
        userCaseEventRepository.findByUserCase_IdOrderByOccurredAtDesc(userCase.getId()).stream()
            .map(e -> new AccountExportResponse.Event(e.getEventType().name(), e.getOccurredAt()))
            .toList();

    return new AccountExportResponse.Case(
        userCase.getId(),
        userCase.getProcedure().getCode(),
        userCase.getStatus().name(),
        userCase.getCurrentRevision() != null
            ? userCase.getCurrentRevision().getRevisionNumber()
            : null,
        userCase.getCreatedAt(),
        userCase.getUpdatedAt(),
        steps,
        documents,
        fees,
        events);
  }
}
