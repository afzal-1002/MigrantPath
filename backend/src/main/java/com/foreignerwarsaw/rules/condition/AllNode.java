package com.foreignerwarsaw.rules.condition;

import java.util.List;

/** {@code {"all": [...]}} - PASS only if every child passes (brief §31). */
public record AllNode(List<ConditionNode> children) implements ConditionNode {}
