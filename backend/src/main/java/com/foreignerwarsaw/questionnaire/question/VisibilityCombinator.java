package com.foreignerwarsaw.questionnaire.question;

/**
 * How a {@link QuestionnaireQuestion}'s {@code QuestionDependency} rows combine when more than one
 * exists (brief §70). {@code ALL} (AND, the default) is correct for "show salary only once there is
 * a job offer AND purpose includes work"-style questions; {@code ANY} (OR) is correct for "show
 * salary if purpose includes WORK or HIGHLY_QUALIFIED_WORK." A question with zero dependency rows
 * is always visible, regardless of this value.
 */
public enum VisibilityCombinator {
  ALL,
  ANY
}
