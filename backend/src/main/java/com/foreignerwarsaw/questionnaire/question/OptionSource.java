package com.foreignerwarsaw.questionnaire.question;

/**
 * Where a {@link QuestionnaireQuestion}'s selectable options come from (brief §10/§11). {@code
 * STATIC} options are {@link com.foreignerwarsaw.questionnaire.option.QuestionOption} rows owned by
 * this question; every other value delegates to the matching Phase 3 reference-data endpoint
 * (Country/Region/City/District) instead of duplicating hundreds of rows into {@code
 * question_options}.
 */
public enum OptionSource {
  STATIC,
  REFERENCE_COUNTRY,
  REFERENCE_REGION,
  REFERENCE_CITY,
  REFERENCE_DISTRICT
}
