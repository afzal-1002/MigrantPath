package com.foreignerwarsaw.questionnaire.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Incoming {@code PUT /api/v1/assessments/{id}/answers/{questionCode}} payload. Exactly one of the
 * value fields should be set, matching the question's {@code answerType} - {@code
 * AssessmentValidationService} enforces this rather than trusting the client (brief §27/§61).
 *
 * <p>{@code unsure} is boxed ({@code Boolean}), not primitive: a client omits every field it isn't
 * setting (e.g. {@code {"booleanValue":true}}), and Jackson's record deserialization maps an absent
 * JSON property to Java {@code null} before any primitive coercion happens - binding that {@code
 * null} to a primitive {@code boolean} parameter fails hard ({@code
 * HttpMessageNotReadableException}) rather than defaulting to {@code false}. The compact
 * constructor below normalizes {@code null} to {@code false} once, so every other class can keep
 * treating this as a plain non-null boolean.
 */
public record AnswerRequest(
    String stringValue,
    Boolean booleanValue,
    Long integerValue,
    BigDecimal decimalValue,
    LocalDate dateValue,
    String referenceCode,
    List<String> selectedOptionCodes,
    Boolean unsure) {

  public AnswerRequest {
    unsure = unsure != null && unsure;
  }
}
