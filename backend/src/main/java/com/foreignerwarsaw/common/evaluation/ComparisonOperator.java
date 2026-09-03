package com.foreignerwarsaw.common.evaluation;

/**
 * The fixed comparison-operator vocabulary shared by {@code QuestionDependency} (Phase 5 - "should
 * this question be shown") and {@code RuleVersion.conditionTree} leaves (Phase 6 - "does this rule
 * match") - one evaluator implementation ({@link ConditionEvaluator}), never two incompatible ones
 * (IMPLEMENTATION_PLAN.md Phase 5 task 5.2, brief §14).
 *
 * <p>{@code ALL}/{@code ANY} are deliberately NOT operators here - they are how multiple conditions
 * *combine* (a per-question combinator for Phase 5, a tree node for Phase 6), not a comparison
 * against a value. {@code NOT} is likewise a Phase 6 condition-tree node, not an operator - see
 * {@code com.foreignerwarsaw.rules.condition.ConditionNode}.
 *
 * <p>{@code IS_MEMBER_OF_COUNTRY_GROUP}/{@code IS_NOT_MEMBER_OF_COUNTRY_GROUP} (Phase 6, brief §25)
 * are declared here (the one shared vocabulary) but are <b>not</b> handled by {@link
 * ConditionEvaluator} - resolving them needs a live call to {@code CountryClassificationService},
 * which a pure, dependency-free static evaluator can't make. {@code
 * com.foreignerwarsaw.rules.evaluation.RuleEvaluator} intercepts these two before ever delegating
 * to {@link ConditionEvaluator}. {@code DURATION_*} operators from the brief's own suggested list
 * are deliberately not included - no fact this codebase computes is a genuine duration yet (brief
 * §45's own caution against speculative, untested operators); add them only alongside a real
 * duration fact.
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
  BETWEEN,
  DATE_BEFORE,
  DATE_BEFORE_OR_EQUAL,
  DATE_AFTER,
  DATE_AFTER_OR_EQUAL,
  IS_MEMBER_OF_COUNTRY_GROUP,
  IS_NOT_MEMBER_OF_COUNTRY_GROUP
}
