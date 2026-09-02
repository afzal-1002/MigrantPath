package com.foreignerwarsaw.questionnaire.question;

/**
 * What an answer *means*, independent of its {@link QuestionType} widget (brief §8) - e.g. {@code
 * MONTHLY_GROSS_SALARY} is a {@code DECIMAL} widget whose semantic meaning is {@code MONEY}, so a
 * future Phase 6 rule knows it's comparing a currency amount, not an arbitrary number. Kept as a
 * small closed set for now (only {@code MONEY} is actually used by the seeded MVP questionnaire) -
 * extend as a real need appears rather than pre-building a large taxonomy.
 */
public enum SemanticDataType {
  GENERIC,
  MONEY
}
