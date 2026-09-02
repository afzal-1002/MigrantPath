package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.questionnaire.assessment.dto.AssessmentDetailResponse;
import com.foreignerwarsaw.questionnaire.core.SectionTitles;
import com.foreignerwarsaw.questionnaire.core.dto.AnswerResponse;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionDefinitionResponse;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionOptionResponse;
import com.foreignerwarsaw.questionnaire.core.dto.SectionResponse;
import com.foreignerwarsaw.questionnaire.option.QuestionOption;
import com.foreignerwarsaw.questionnaire.option.QuestionOptionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the backend-authoritative {@link AssessmentDetailResponse} (brief §30) - the single
 * read path both the "start" and "get" endpoints return, so a client always sees the same shape
 * whether it just started or is resuming.
 */
@Service
public class AssessmentQueryService {

  private final AssessmentCompletionService assessmentCompletionService;
  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final QuestionOptionRepository questionOptionRepository;

  public AssessmentQueryService(
      AssessmentCompletionService assessmentCompletionService,
      AssessmentAnswerRepository assessmentAnswerRepository,
      QuestionOptionRepository questionOptionRepository) {
    this.assessmentCompletionService = assessmentCompletionService;
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.questionOptionRepository = questionOptionRepository;
  }

  @Transactional(readOnly = true)
  public AssessmentDetailResponse toDetailResponse(Assessment assessment) {
    AssessmentCompletionService.VisibilitySnapshot snapshot =
        assessmentCompletionService.currentVisibility(assessment);

    Map<UUID, AssessmentAnswer> answerByQuestionId =
        assessmentAnswerRepository.findByAssessment_Id(assessment.getId()).stream()
            .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

    List<QuestionnaireQuestion> visibleOrdered =
        snapshot.allQuestions().stream()
            .filter(qq -> snapshot.visibleQuestionnaireQuestionIds().contains(qq.getId()))
            .toList();

    Map<String, Integer> sectionSortOrder = new LinkedHashMap<>();
    List<QuestionDefinitionResponse> questionResponses =
        visibleOrdered.stream()
            .map(
                qq -> {
                  sectionSortOrder.putIfAbsent(qq.getSectionCode(), qq.getSortOrder());
                  List<QuestionOptionResponse> options =
                      questionOptionRepository
                          .findByQuestionnaireQuestion_IdOrderBySortOrder(qq.getId())
                          .stream()
                          .filter(QuestionOption::isActive)
                          .map(QuestionOptionResponse::from)
                          .toList();
                  QuestionDefinitionResponse definition =
                      QuestionDefinitionResponse.definitionOnly(qq, options);
                  AssessmentAnswer answer = answerByQuestionId.get(qq.getQuestion().getId());
                  return answer != null && answer.isApplicable()
                      ? definition.withAnswer(toAnswerResponse(answer))
                      : definition;
                })
            .toList();

    List<SectionResponse> sections =
        sectionSortOrder.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(
                entry ->
                    new SectionResponse(
                        entry.getKey(), SectionTitles.titleFor(entry.getKey()), entry.getValue()))
            .toList();

    List<MissingQuestion> missing =
        assessmentCompletionService.findMissingRequiredQuestions(assessment);
    int progress = assessmentCompletionService.progressPercent(assessment);

    return new AssessmentDetailResponse(
        assessment.getId(),
        assessment.getStatus().name(),
        assessment.getQuestionnaire().getCode(),
        assessment.getQuestionnaireVersion().getId(),
        assessment.getStartedAt(),
        assessment.getCompletedAt(),
        progress,
        sections,
        questionResponses,
        missing);
  }

  private AnswerResponse toAnswerResponse(AssessmentAnswer answer) {
    List<String> selectedOptionCodes =
        answer.getSelectedOptions().isEmpty()
            ? null
            : answer.getSelectedOptions().stream()
                .map(AssessmentAnswerOption::getOptionCode)
                .toList();
    return new AnswerResponse(
        answer.getStringValue(),
        answer.getBooleanValue(),
        answer.getIntegerValue(),
        answer.getDecimalValue(),
        answer.getDateValue(),
        answer.getReferenceCode(),
        selectedOptionCodes,
        answer.isUnsure());
  }
}
