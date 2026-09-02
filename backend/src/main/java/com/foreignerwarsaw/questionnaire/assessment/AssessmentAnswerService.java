package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.assessment.dto.AnswerRequest;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependencyRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import com.foreignerwarsaw.questionnaire.visibility.QuestionVisibilityService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one answer at a time (brief §31 - the simpler of the two suggested shapes; see
 * PHASE_5_REPORT.md "Deviations" for why no bulk {@code PATCH .../answers} exists) and, on every
 * write, recomputes every answer's {@code applicable} flag for the whole assessment (brief §28/§29)
 * - the single place branch-driven hide/show and stale-answer bookkeeping happens, never duplicated
 * per call site.
 */
@Service
public class AssessmentAnswerService {

  private final AssessmentRepository assessmentRepository;
  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionDependencyRepository questionDependencyRepository;
  private final QuestionVisibilityService questionVisibilityService;
  private final AssessmentValidationService assessmentValidationService;
  private final Clock clock;

  public AssessmentAnswerService(
      AssessmentRepository assessmentRepository,
      AssessmentAnswerRepository assessmentAnswerRepository,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionDependencyRepository questionDependencyRepository,
      QuestionVisibilityService questionVisibilityService,
      AssessmentValidationService assessmentValidationService,
      Clock clock) {
    this.assessmentRepository = assessmentRepository;
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionDependencyRepository = questionDependencyRepository;
    this.questionVisibilityService = questionVisibilityService;
    this.assessmentValidationService = assessmentValidationService;
    this.clock = clock;
  }

  /**
   * Takes the assessment id, not an {@link Assessment} instance - the caller (the controller, after
   * its own ownership-checking {@code AssessmentService#getOwned} call) only ever holds a reference
   * that's already detached by the time this method runs (that read happened in a prior,
   * already-committed {@code @Transactional(readOnly = true)} method) - mutating a detached entity
   * silently produces no SQL at all. Re-fetching by id here guarantees a managed entity within this
   * method's own transaction, the same "re-fetch inside its own transaction" discipline {@code
   * ProcedureVersionService} uses for exactly this reason.
   */
  @Transactional
  public void saveAnswer(UUID assessmentId, String questionCode, AnswerRequest request) {
    Assessment assessment =
        assessmentRepository
            .findByIdFetchingVersion(assessmentId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND", "Not found"));
    requireInProgress(assessment);

    List<QuestionnaireQuestion> allQuestions =
        questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
            assessment.getQuestionnaireVersion().getId());
    QuestionnaireQuestion target =
        allQuestions.stream()
            .filter(qq -> qq.getQuestion().getCode().equals(questionCode))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "QUESTION_NOT_FOUND",
                        "No question with code " + questionCode + " in this questionnaire"));

    List<QuestionDependency> allDependencies =
        questionDependencyRepository.findByQuestionnaireVersion_Id(
            assessment.getQuestionnaireVersion().getId());

    Set<UUID> visibleBeforeWrite =
        questionVisibilityService.computeVisibleQuestionnaireQuestionIds(
            allQuestions, allDependencies, currentAnswerValues(assessment));
    if (!visibleBeforeWrite.contains(target.getId())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "QUESTION_NOT_APPLICABLE",
          "This question is not currently applicable given your other answers.");
    }

    assessmentValidationService.validate(target, request);

    // Populate the entity fully before it's ever handed to the repository - never persist() a
    // blank "unanswered" row and mutate it afterward. A new entity's fields must already reflect
    // this write by the time save() runs, in case Hibernate flushes eagerly (e.g. the SELECT
    // inside recomputeApplicability below, under the default AUTO flush mode) before this method
    // returns.
    AssessmentAnswer answer =
        assessmentAnswerRepository
            .findByAssessment_IdAndQuestion_Id(assessment.getId(), target.getQuestion().getId())
            .orElseGet(() -> AssessmentAnswer.unanswered(assessment, target.getQuestion()));
    applyValue(answer, target.getQuestion().getQuestionType(), request);
    assessmentAnswerRepository.save(answer);

    recomputeApplicability(assessment, allQuestions, allDependencies);
    assessment.touch(Instant.now(clock));
  }

  /**
   * Re-run after every answer write (and once right after {@link AssessmentService#start}/restart,
   * when there are no answers yet, a no-op). Never trusts a previously-stored {@code applicable}
   * flag as an input - always recomputed from the raw answer values, so it can never drift from
   * what the dependency graph actually implies.
   */
  @Transactional
  public void recomputeApplicability(
      Assessment assessment,
      List<QuestionnaireQuestion> allQuestions,
      List<QuestionDependency> allDependencies) {
    List<AssessmentAnswer> answers =
        assessmentAnswerRepository.findByAssessment_Id(assessment.getId());
    Map<UUID, Object> answerValuesByQuestionId = toAnswerValueMap(answers);

    Set<UUID> visibleQuestionnaireQuestionIds =
        questionVisibilityService.computeVisibleQuestionnaireQuestionIds(
            allQuestions, allDependencies, answerValuesByQuestionId);

    Map<UUID, UUID> questionnaireQuestionIdByQuestionId =
        allQuestions.stream()
            .collect(
                Collectors.toMap(
                    qq -> qq.getQuestion().getId(), QuestionnaireQuestion::getId, (a, b) -> a));

    for (AssessmentAnswer answer : answers) {
      UUID questionnaireQuestionId =
          questionnaireQuestionIdByQuestionId.get(answer.getQuestion().getId());
      boolean applicable =
          questionnaireQuestionId != null
              && visibleQuestionnaireQuestionIds.contains(questionnaireQuestionId);
      answer.setApplicable(applicable);
    }
  }

  /**
   * The raw answer value per stable Question id, regardless of the answer's currently-stored {@code
   * applicable} flag - visibility is always recomputed from scratch, never trusting a
   * possibly-stale cached flag (see {@link #recomputeApplicability}'s Javadoc).
   */
  public Map<UUID, Object> currentAnswerValues(Assessment assessment) {
    return toAnswerValueMap(assessmentAnswerRepository.findByAssessment_Id(assessment.getId()));
  }

  /**
   * {@code Collectors.toMap} rejects a null value (an "unsure" answer's {@code logicalValue()})
   * outright, so this builds the map by hand rather than via the stream collector.
   */
  private Map<UUID, Object> toAnswerValueMap(List<AssessmentAnswer> answers) {
    Map<UUID, Object> values = new HashMap<>();
    for (AssessmentAnswer answer : answers) {
      values.put(answer.getQuestion().getId(), answer.logicalValue());
    }
    return values;
  }

  private void applyValue(AssessmentAnswer answer, QuestionType type, AnswerRequest request) {
    Instant now = Instant.now(clock);
    if (request.unsure()) {
      answer.markUnsure(now);
      return;
    }
    switch (type) {
      case BOOLEAN -> answer.setBooleanValue(request.booleanValue(), now);
      case TEXT -> answer.setStringValue(request.stringValue(), now);
      case INTEGER -> answer.setIntegerValue(request.integerValue(), now);
      case DECIMAL -> answer.setDecimalValue(request.decimalValue(), now);
      case DATE -> answer.setDateValue(request.dateValue(), now);
      case COUNTRY, REGION, CITY, DISTRICT, SINGLE_SELECT ->
          answer.setReferenceCode(request.referenceCode(), now);
      case MULTI_SELECT ->
          answer.setSelectedOptionCodes(new HashSet<>(request.selectedOptionCodes()), now);
    }
  }

  /**
   * Copies one already-valid answer onto a different (new) assessment verbatim, bypassing
   * visibility/type validation - used only by {@code AssessmentService#restart}'s "start a new
   * assessment from a completed one's answers" flow (brief §36), where the source values are by
   * definition already valid instances of their question's type. The caller is responsible for
   * calling {@link #recomputeApplicability} once after copying every answer.
   */
  @Transactional
  public void copyAnswerToNewAssessment(Assessment targetAssessment, AssessmentAnswer source) {
    AssessmentAnswer copy = AssessmentAnswer.unanswered(targetAssessment, source.getQuestion());
    Instant answeredAt = source.getAnsweredAt();
    if (source.isUnsure()) {
      copy.markUnsure(answeredAt);
    } else if (source.getBooleanValue() != null) {
      copy.setBooleanValue(source.getBooleanValue(), answeredAt);
    } else if (source.getIntegerValue() != null) {
      copy.setIntegerValue(source.getIntegerValue(), answeredAt);
    } else if (source.getDecimalValue() != null) {
      copy.setDecimalValue(source.getDecimalValue(), answeredAt);
    } else if (source.getDateValue() != null) {
      copy.setDateValue(source.getDateValue(), answeredAt);
    } else if (source.getReferenceCode() != null) {
      copy.setReferenceCode(source.getReferenceCode(), answeredAt);
    } else if (!source.getSelectedOptions().isEmpty()) {
      Set<String> codes =
          source.getSelectedOptions().stream()
              .map(AssessmentAnswerOption::getOptionCode)
              .collect(Collectors.toSet());
      copy.setSelectedOptionCodes(codes, answeredAt);
    } else {
      copy.setStringValue(source.getStringValue(), answeredAt);
    }
    assessmentAnswerRepository.save(copy);
  }

  private void requireInProgress(Assessment assessment) {
    if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ASSESSMENT_NOT_IN_PROGRESS",
          "This assessment is "
              + assessment.getStatus()
              + " and can no longer be edited directly.");
    }
  }
}
