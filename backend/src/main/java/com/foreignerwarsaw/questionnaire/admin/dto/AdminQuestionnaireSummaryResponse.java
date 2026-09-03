package com.foreignerwarsaw.questionnaire.admin.dto;

import com.foreignerwarsaw.questionnaire.core.Questionnaire;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;

public record AdminQuestionnaireSummaryResponse(
    String code,
    Integer activeVersionNumber,
    Integer latestVersionNumber,
    String latestVersionStatus) {

  public static AdminQuestionnaireSummaryResponse from(
      Questionnaire q, QuestionnaireVersion active, QuestionnaireVersion latest) {
    return new AdminQuestionnaireSummaryResponse(
        q.getCode(),
        active != null ? active.getVersionNumber() : null,
        latest != null ? latest.getVersionNumber() : null,
        latest != null ? latest.getStatus().name() : null);
  }
}
