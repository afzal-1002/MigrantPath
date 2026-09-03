package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;

/**
 * One structured trace entry (brief §33/§35) - the debugging/admin/test/explanation backbone.
 * {@code conditionCode} is the leaf's own stable {@code code} when the rule author gave one, never
 * a positional array index (brief §35: "do not rely on array index such as condition #4") - {@code
 * path} (e.g. {@code "all[0].any[1]"}) is the fallback identifier when it didn't.
 *
 * <p>{@code message} is a structured, technical debug string (fact/operator/values involved) -
 * never user-facing prose (brief §34/§90: Phase 7 converts {@code explanationKey} into translated,
 * legally-reviewed text, this class never invents any).
 */
public record ConditionTrace(
    String conditionCode,
    String path,
    String fact,
    ComparisonOperator operator,
    ConditionResult result,
    String explanationKey,
    String message) {}
