package com.foreignerwarsaw.questionnaire.admin.dto;

import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminQuestionnaireVersionDetailResponse(
    UUID id,
    String questionnaireCode,
    int versionNumber,
    String title,
    String description,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    long lockVersion,
    String createdByEmail,
    String submittedByEmail,
    String approvedByEmail,
    String publishedByEmail,
    Instant publishedAt,
    List<QuestionSummary> questions) {

  public record QuestionSummary(
      String questionCode,
      String sectionCode,
      String label,
      boolean required,
      int sortOrder,
      String questionType) {
    static QuestionSummary from(QuestionnaireQuestion q) {
      return new QuestionSummary(
          q.getQuestion().getCode(),
          q.getSectionCode(),
          q.getLabel(),
          q.isRequired(),
          q.getSortOrder(),
          q.getQuestion().getQuestionType().name());
    }
  }

  public static AdminQuestionnaireVersionDetailResponse from(
      QuestionnaireVersion v, List<QuestionnaireQuestion> questions) {
    return new AdminQuestionnaireVersionDetailResponse(
        v.getId(),
        v.getQuestionnaire().getCode(),
        v.getVersionNumber(),
        v.getTitle(),
        v.getDescription(),
        v.getStatus().name(),
        v.getEffectiveFrom(),
        v.getEffectiveTo(),
        v.getLockVersion(),
        v.getCreatedBy() != null ? v.getCreatedBy().getEmail() : null,
        v.getSubmittedBy() != null ? v.getSubmittedBy().getEmail() : null,
        v.getApprovedBy() != null ? v.getApprovedBy().getEmail() : null,
        v.getPublishedBy() != null ? v.getPublishedBy().getEmail() : null,
        v.getPublishedAt(),
        questions.stream().map(QuestionSummary::from).toList());
  }
}
