package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.common.web.ApiError;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependencyRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import com.foreignerwarsaw.questionnaire.visibility.QuestionVisibilityService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Progress + completion (brief §36/§49) - "missing" and "progress" are both a function of only the
 * *currently visible* required questions, never the whole database-wide question count (brief §49's
 * explicit "do not calculate answered / total in database" warning - a hidden branch must never
 * distort progress).
 */
@Service
public class AssessmentCompletionService {

  private final AssessmentRepository assessmentRepository;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionDependencyRepository questionDependencyRepository;
  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final AssessmentAnswerService assessmentAnswerService;
  private final QuestionVisibilityService questionVisibilityService;
  private final Clock clock;

  public AssessmentCompletionService(
      AssessmentRepository assessmentRepository,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionDependencyRepository questionDependencyRepository,
      AssessmentAnswerRepository assessmentAnswerRepository,
      AssessmentAnswerService assessmentAnswerService,
      QuestionVisibilityService questionVisibilityService,
      Clock clock) {
    this.assessmentRepository = assessmentRepository;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionDependencyRepository = questionDependencyRepository;
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.assessmentAnswerService = assessmentAnswerService;
    this.questionVisibilityService = questionVisibilityService;
    this.clock = clock;
  }

  public record VisibilitySnapshot(
      List<QuestionnaireQuestion> allQuestions, Set<UUID> visibleQuestionnaireQuestionIds) {}

  @Transactional(readOnly = true)
  public VisibilitySnapshot currentVisibility(Assessment assessment) {
    List<QuestionnaireQuestion> allQuestions =
        questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
            assessment.getQuestionnaireVersion().getId());
    List<QuestionDependency> dependencies =
        questionDependencyRepository.findByQuestionnaireVersion_Id(
            assessment.getQuestionnaireVersion().getId());
    Set<UUID> visible =
        questionVisibilityService.computeVisibleQuestionnaireQuestionIds(
            allQuestions, dependencies, assessmentAnswerService.currentAnswerValues(assessment));
    return new VisibilitySnapshot(allQuestions, visible);
  }

  @Transactional(readOnly = true)
  public List<MissingQuestion> findMissingRequiredQuestions(Assessment assessment) {
    VisibilitySnapshot snapshot = currentVisibility(assessment);
    Set<UUID> answeredQuestionIds =
        assessmentAnswerRepository.findByAssessment_Id(assessment.getId()).stream()
            .filter(AssessmentAnswer::isApplicable)
            .map(a -> a.getQuestion().getId())
            .collect(Collectors.toSet());

    return snapshot.allQuestions().stream()
        .filter(qq -> snapshot.visibleQuestionnaireQuestionIds().contains(qq.getId()))
        .filter(QuestionnaireQuestion::isRequired)
        .filter(qq -> !answeredQuestionIds.contains(qq.getQuestion().getId()))
        .map(
            qq ->
                new MissingQuestion(qq.getQuestion().getCode(), qq.getLabel(), qq.getSectionCode()))
        .toList();
  }

  /**
   * {@code answeredCount / requiredVisibleCount} over only the currently applicable path (brief
   * §49) - 100 once every currently-visible required question has an applicable answer, regardless
   * of how many more questions exist in branches the user never entered.
   */
  @Transactional(readOnly = true)
  public int progressPercent(Assessment assessment) {
    VisibilitySnapshot snapshot = currentVisibility(assessment);
    long requiredVisible =
        snapshot.allQuestions().stream()
            .filter(qq -> snapshot.visibleQuestionnaireQuestionIds().contains(qq.getId()))
            .filter(QuestionnaireQuestion::isRequired)
            .count();
    if (requiredVisible == 0) {
      return 100;
    }
    long missing = findMissingRequiredQuestions(assessment).size();
    return (int) Math.round(100.0 * (requiredVisible - missing) / requiredVisible);
  }

  /**
   * Verifies every currently-visible required question has an applicable answer (brief §36), then
   * marks the assessment {@code COMPLETED}. A completed assessment is immutable to direct answer
   * edits from then on (brief §36) - {@link AssessmentAnswerService#saveAnswer} enforces that via
   * {@code requireInProgress}.
   */
  @Transactional
  public void complete(UUID assessmentId) {
    // Re-fetched by id, not taken as a parameter object - see AssessmentAnswerService#saveAnswer's
    // Javadoc for why: the caller's own reference is already detached, and mutating a detached
    // entity produces no SQL at all.
    Assessment assessment =
        assessmentRepository
            .findByIdFetchingVersion(assessmentId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND", "Not found"));
    if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ASSESSMENT_NOT_IN_PROGRESS",
          "This assessment is " + assessment.getStatus() + ", not in progress.");
    }
    List<MissingQuestion> missing = findMissingRequiredQuestions(assessment);
    if (!missing.isEmpty()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "ASSESSMENT_INCOMPLETE",
          "Answer every required question before completing the assessment.",
          missing.stream()
              .map(m -> new ApiError.FieldViolation(m.questionCode(), "This question is required."))
              .toList());
    }
    assessment.complete(Instant.now(clock));
  }
}
