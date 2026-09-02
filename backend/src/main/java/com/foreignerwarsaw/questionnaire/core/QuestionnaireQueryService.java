package com.foreignerwarsaw.questionnaire.core;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionDefinitionResponse;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionOptionResponse;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionnaireStructureResponse;
import com.foreignerwarsaw.questionnaire.core.dto.SectionResponse;
import com.foreignerwarsaw.questionnaire.option.QuestionOption;
import com.foreignerwarsaw.questionnaire.option.QuestionOptionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public read path for questionnaire structure (brief §31) - resolves only the currently active
 * PUBLISHED {@link QuestionnaireVersion} via the Active-Version Predicate
 * (docs/database/DATABASE.md §0), same discipline as {@code ProcedureQueryService}.
 */
@Service
public class QuestionnaireQueryService {

  private final QuestionnaireVersionRepository questionnaireVersionRepository;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionOptionRepository questionOptionRepository;
  private final Clock clock;

  public QuestionnaireQueryService(
      QuestionnaireVersionRepository questionnaireVersionRepository,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionOptionRepository questionOptionRepository,
      Clock clock) {
    this.questionnaireVersionRepository = questionnaireVersionRepository;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionOptionRepository = questionOptionRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public QuestionnaireVersion resolveActiveVersion(String questionnaireCode) {
    return questionnaireVersionRepository
        .findActivePublishedVersion(questionnaireCode, LocalDate.now(clock))
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "NO_ACTIVE_QUESTIONNAIRE_VERSION",
                    "No published questionnaire version is currently active for "
                        + questionnaireCode));
  }

  @Transactional(readOnly = true)
  public QuestionnaireStructureResponse getActiveStructure(String questionnaireCode) {
    QuestionnaireVersion version = resolveActiveVersion(questionnaireCode);
    return toStructureResponse(version);
  }

  public QuestionnaireStructureResponse toStructureResponse(QuestionnaireVersion version) {
    List<QuestionnaireQuestion> questions =
        questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
            version.getId());

    Map<String, Integer> sectionSortOrder = new LinkedHashMap<>();
    List<QuestionDefinitionResponse> questionResponses =
        questions.stream()
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
                  return QuestionDefinitionResponse.definitionOnly(qq, options);
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

    return new QuestionnaireStructureResponse(
        version.getQuestionnaire().getCode(),
        version.getId(),
        version.getVersionNumber(),
        version.getTitle(),
        version.getDescription(),
        sections,
        questionResponses);
  }

  /**
   * Used by {@code AssessmentService} to load a specific (possibly no-longer-active) version an
   * existing Assessment is permanently bound to (brief §4) - not the Active-Version Predicate.
   */
  @Transactional(readOnly = true)
  public QuestionnaireVersion getVersionById(UUID versionId) {
    return questionnaireVersionRepository
        .findByIdFetchingQuestionnaire(versionId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "QUESTIONNAIRE_VERSION_NOT_FOUND", "Not found"));
  }
}
