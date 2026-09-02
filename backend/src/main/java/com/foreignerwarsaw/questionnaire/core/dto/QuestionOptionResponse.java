package com.foreignerwarsaw.questionnaire.core.dto;

import com.foreignerwarsaw.questionnaire.option.QuestionOption;

public record QuestionOptionResponse(String code, String label, String description) {

  public static QuestionOptionResponse from(QuestionOption option) {
    return new QuestionOptionResponse(option.getCode(), option.getLabel(), option.getDescription());
  }
}
