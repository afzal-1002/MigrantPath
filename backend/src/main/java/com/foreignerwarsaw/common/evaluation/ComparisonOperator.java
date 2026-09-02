package com.foreignerwarsaw.common.evaluation;

/**
 * The fixed comparison-operator vocabulary shared by {@code QuestionDependency} (Phase 5 - "should
 * this question be shown") and, later, Phase 6's {@code RuleCondition} ("does this rule match") -
 * one evaluator implementation ({@link ConditionEvaluator}), never two incompatible ones
 * (IMPLEMENTATION_PLAN.md Phase 5 task 5.2, brief §14).
 *
 * <p>{@code ALL}/{@code ANY} are deliberately NOT operators here - they are how multiple conditions
 * *combine* (a per-question combinator, e.g. {@code QuestionnaireQuestion.visibilityCombinator}),
 * not a comparison against a value. Modeling them as a combinator rather than a 15th operator keeps
 * this enum a closed set of actual comparisons.
 */
public enum ComparisonOperator {
  EQUALS,
  NOT_EQUALS,
  IN,
  NOT_IN,
  CONTAINS,
  NOT_CONTAINS,
  EXISTS,
  NOT_EXISTS,
  GREATER_THAN,
  GREATER_THAN_OR_EQUAL,
  LESS_THAN,
  LESS_THAN_OR_EQUAL,
  DATE_BEFORE,
  DATE_AFTER
}
