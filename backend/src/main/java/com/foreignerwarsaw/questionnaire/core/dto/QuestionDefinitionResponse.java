package com.foreignerwarsaw.questionnaire.core.dto;

import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import java.util.List;
import java.util.UUID;

/**
 * The full renderable definition of one question - everything the Angular generic renderer (brief
 * §50) needs to draw the right widget with no per-question Angular code. {@code answer} is only
 * populated when this appears inside an {@code AssessmentDetailResponse} (brief §55's "hidden
 * answers never appear" - a question absent from that response's list was never included at all).
 */
public record QuestionDefinitionResponse(
    UUID questionnaireQuestionId,
    String questionCode,
    String fieldKey,
    String sectionCode,
    String label,
    String helpText,
    boolean required,
    int sortOrder,
    String answerType,
    String optionSource,
    boolean allowUnsure,
    List<QuestionOptionResponse> options,
    AnswerResponse answer) {

  public static QuestionDefinitionResponse definitionOnly(
      QuestionnaireQuestion questionnaireQuestion, List<QuestionOptionResponse> options) {
    return new QuestionDefinitionResponse(
        questionnaireQuestion.getId(),
        questionnaireQuestion.getQuestion().getCode(),
        questionnaireQuestion.getQuestion().getFieldKey(),
        questionnaireQuestion.getSectionCode(),
        questionnaireQuestion.getLabel(),
        questionnaireQuestion.getHelpText(),
        questionnaireQuestion.isRequired(),
        questionnaireQuestion.getSortOrder(),
        questionnaireQuestion.getQuestion().getQuestionType().name(),
        questionnaireQuestion.getOptionSource().name(),
        questionnaireQuestion.isAllowUnsure(),
        options,
        null);
  }

  public QuestionDefinitionResponse withAnswer(AnswerResponse answer) {
    return new QuestionDefinitionResponse(
        questionnaireQuestionId,
        questionCode,
        fieldKey,
        sectionCode,
        label,
        helpText,
        required,
        sortOrder,
        answerType,
        optionSource,
        allowUnsure,
        options,
        answer);
  }
}
