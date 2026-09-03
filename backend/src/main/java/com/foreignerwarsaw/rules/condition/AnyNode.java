package com.foreignerwarsaw.rules.condition;

import java.util.List;

/** {@code {"any": [...]}} - PASS if at least one child passes (brief §31). */
public record AnyNode(List<ConditionNode> children) implements ConditionNode {}
