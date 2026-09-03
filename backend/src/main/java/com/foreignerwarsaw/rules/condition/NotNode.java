package com.foreignerwarsaw.rules.condition;

/**
 * {@code {"not": {...}}} - inverts its single child's result (PASS&lt;-&gt;FAIL; MISSING and ERROR
 * pass through unchanged, brief §31).
 */
public record NotNode(ConditionNode child) implements ConditionNode {}
