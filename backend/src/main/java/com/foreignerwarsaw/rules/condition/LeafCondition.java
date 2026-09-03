package com.foreignerwarsaw.rules.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;

/**
 * One leaf comparison (brief §8) - {@code {"fact": "...", "operator": "...", "value": ...}} (a
 * literal comparand) or {@code {"fact": "...", "operator": "...", "threshold": "CODE"}} (resolved
 * against a versioned {@code Threshold} at evaluation time, never baked in - brief §18). Exactly
 * one of {@link #value}/{@link #threshold} is non-null, except for {@code EXISTS}/{@code
 * NOT_EXISTS}, which need neither.
 *
 * <p>{@code code} (brief §35, e.g. {@code "BLUE_CARD_SALARY"}) is a stable per-condition identifier
 * for tracing/explanation - never an array index, so a displayed reason/test assertion survives the
 * tree being reordered. Nullable: not every condition needs individual tracing beyond its position.
 */
public record LeafCondition(
    String code,
    String fact,
    ComparisonOperator operator,
    JsonNode value,
    String threshold,
    String explanationKey)
    implements ConditionNode {}
