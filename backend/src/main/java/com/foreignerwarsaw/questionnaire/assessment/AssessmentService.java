package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireQueryService;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependencyRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment lifecycle - start/resume, restart, ownership-checked read, list (brief §23/§32-§36).
 * Authenticated-only throughout: every method takes an already-authenticated {@link User}, never an
 * anonymous session token (see PHASE_5_REPORT.md "Deviations").
 */
@Service
public class AssessmentService {

  private final AssessmentRepository assessmentRepository;
  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final QuestionnaireQueryService questionnaireQueryService;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionDependencyRepository questionDependencyRepository;
  private final AssessmentAnswerService assessmentAnswerService;
  private final Clock clock;

  public AssessmentService(
      AssessmentRepository assessmentRepository,
      AssessmentAnswerRepository assessmentAnswerRepository,
      QuestionnaireQueryService questionnaireQueryService,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionDependencyRepository questionDependencyRepository,
      AssessmentAnswerService assessmentAnswerService,
      Clock clock) {
    this.assessmentRepository = assessmentRepository;
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.questionnaireQueryService = questionnaireQueryService;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionDependencyRepository = questionDependencyRepository;
    this.assessmentAnswerService = assessmentAnswerService;
    this.clock = clock;
  }

  /**
   * Starting again while an assessment is already {@code IN_PROGRESS} simply resumes it (brief §33)
   * - at most one {@code IN_PROGRESS} assessment per user per questionnaire identity exists at all
   * (brief §34, enforced again at the database level by {@code
   * assessments_one_in_progress_per_user_questionnaire_uq}), so "start" and "resume" are the same
   * operation from the API's point of view.
   */
  @Transactional
  public Assessment start(User user, String questionnaireCode) {
    QuestionnaireVersion activeVersion =
        questionnaireQueryService.resolveActiveVersion(questionnaireCode);

    Optional<Assessment> inProgress =
        assessmentRepository.findByUser_IdAndQuestionnaire_IdAndStatus(
            user.getId(), activeVersion.getQuestionnaire().getId(), AssessmentStatus.IN_PROGRESS);
    if (inProgress.isPresent()) {
      return inProgress.get();
    }

    Assessment assessment = Assessment.start(user, activeVersion, Instant.now(clock));
    return assessmentRepository.save(assessment);
  }

  @Transactional(readOnly = true)
  public Assessment getOwned(UUID assessmentId, UUID userId) {
    Assessment assessment =
        assessmentRepository
            .findByIdFetchingVersion(assessmentId)
            .orElseThrow(AssessmentService::notFound);
    if (!assessment.isOwnedBy(userId)) {
      // 404, not 403 - never confirms an assessment id belongs to someone else (brief §57's IDOR
      // requirement; matches how ProcedureController never distinguishes "doesn't exist" from
      // "not visible to you").
      throw notFound();
    }
    return assessment;
  }

  @Transactional(readOnly = true)
  public List<Assessment> listForUser(UUID userId) {
    return assessmentRepository.findByUser_IdOrderByStartedAtDesc(userId);
  }

  /**
   * brief §35 (explicit restart of an {@code IN_PROGRESS} assessment - a genuine blank slate, no
   * answers carried over) and brief §36 ("update answers" on a {@code COMPLETED} assessment, which
   * instead copies every currently-applicable answer forward as an editable starting point) share
   * one endpoint and one method, distinguished by the superseded assessment's own status.
   */
  @Transactional
  public Assessment restart(UUID assessmentId, UUID userId) {
    Assessment previous = getOwned(assessmentId, userId);
    if (previous.getStatus() != AssessmentStatus.IN_PROGRESS
        && previous.getStatus() != AssessmentStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ASSESSMENT_CANNOT_BE_RESTARTED",
          "Only an in-progress or completed assessment can be restarted.");
    }

    boolean copyForward = previous.getStatus() == AssessmentStatus.COMPLETED;
    Instant now = Instant.now(clock);
    previous.supersede(now);

    QuestionnaireVersion activeVersion =
        questionnaireQueryService.resolveActiveVersion(previous.getQuestionnaire().getCode());
    Assessment next =
        assessmentRepository.save(Assessment.start(previous.getUser(), activeVersion, now));

    if (copyForward) {
      for (AssessmentAnswer answer :
          assessmentAnswerRepository.findByAssessment_Id(previous.getId())) {
        if (answer.isApplicable()) {
          assessmentAnswerService.copyAnswerToNewAssessment(next, answer);
        }
      }
      List<QuestionnaireQuestion> questions =
          questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
              next.getQuestionnaireVersion().getId());
      List<QuestionDependency> dependencies =
          questionDependencyRepository.findByQuestionnaireVersion_Id(
              next.getQuestionnaireVersion().getId());
      assessmentAnswerService.recomputeApplicability(next, questions, dependencies);
    }

    return next;
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND", "Assessment not found");
  }
}
