package com.foreignerwarsaw.questionnaire.question;

/**
 * The UI-widget vocabulary a {@link Question} renders as (brief §7) - distinct from {@link
 * SemanticDataType}, which is what the answer *means* (brief §8). File upload is deliberately not
 * supported (brief §7).
 */
public enum QuestionType {
  BOOLEAN,
  SINGLE_SELECT,
  MULTI_SELECT,
  TEXT,
  INTEGER,
  DECIMAL,
  DATE,
  COUNTRY,
  REGION,
  CITY,
  DISTRICT
}
