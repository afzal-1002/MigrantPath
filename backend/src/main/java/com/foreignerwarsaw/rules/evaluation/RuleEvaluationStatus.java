package com.foreignerwarsaw.rules.evaluation;

/**
 * The whole-rule result (brief §32) - the root {@link ConditionResult} mapped 1:1 ({@code
 * PASS→SATISFIED}, {@code FAIL→NOT_SATISFIED}, {@code MISSING→INDETERMINATE}, {@code ERROR→ERROR}).
 * Deliberately not named {@code ELIGIBLE}/{@code NOT_ELIGIBLE} (brief §32): this is a statement
 * about whether *this rule's* conditions held, not a final user-facing recommendation - Phase 7
 * owns turning one or more of these into a ranked recommendation.
 */
public enum RuleEvaluationStatus {
  SATISFIED,
  NOT_SATISFIED,
  INDETERMINATE,
  ERROR
}
