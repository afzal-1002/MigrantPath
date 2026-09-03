package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The machine-readable result of evaluating one {@code RuleVersion} against one assessment's facts
 * on one date (brief §32) - the Phase 7 contract. Deliberately carries no ranking/recommendation
 * concept (brief §79/§90) - {@link #status} says whether *this rule's* conditions held, nothing
 * about how it compares to any other rule or what a user should do.
 *
 * <p>{@link #ruleType} was added for Phase 7 (Rules Engine brief §16's "reuse Phase 6 rule types"
 * instead of a parallel "rule role" concept) - Phase 7's aggregation policy reads it to tell a
 * required/base condition from an exclusion or purely informational one; Phase 6 itself never reads
 * or branches on it.
 */
public record RuleEvaluationResult(
    UUID ruleId,
    String ruleCode,
    RuleType ruleType,
    UUID ruleVersionId,
    int ruleVersionNumber,
    RuleTargetType targetType,
    String targetCode,
    LocalDate evaluationDate,
    RuleEvaluationStatus status,
    List<ConditionTrace> passedConditions,
    List<ConditionTrace> failedConditions,
    List<ConditionTrace> missingConditions,
    List<ConditionTrace> errorConditions,
    Set<String> missingFacts,
    List<ThresholdUsage> thresholdsUsed,
    List<UUID> sourceIds,
    String explanationKey) {}
