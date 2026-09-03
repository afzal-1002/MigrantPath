package com.foreignerwarsaw.rules.admin.dto;

import java.util.UUID;

/**
 * A pragmatic diff for Rule content (brief §69): the condition tree is opaque JSON, so rather than
 * a misleading "semantic" field-by-field breakdown, this surfaces whether it changed at all plus
 * the two trees side by side for the reviewer to read - {@code explanationKey} is diffed properly
 * since it is a simple scalar.
 */
public record RuleVersionDiffResponse(
    UUID fromVersionId,
    int fromVersionNumber,
    UUID toVersionId,
    int toVersionNumber,
    boolean conditionTreeChanged,
    String fromConditionTree,
    String toConditionTree,
    boolean explanationKeyChanged,
    String fromExplanationKey,
    String toExplanationKey) {}
