package com.foreignerwarsaw.questionnaire.assessment;

/**
 * One currently-visible, required, unanswered question - {@code
 * AssessmentCompletionService#findMissingRequiredQuestions}'s structured result (brief §36).
 */
public record MissingQuestion(String questionCode, String label, String sectionCode) {}
