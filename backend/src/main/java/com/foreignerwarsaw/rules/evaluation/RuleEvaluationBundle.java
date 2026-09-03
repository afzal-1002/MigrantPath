package com.foreignerwarsaw.rules.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every applicable rule's result for one assessment, on one evaluation date (brief §78) - the
 * whole-assessment version of {@link RuleEvaluationResult}, grouped by target so Phase 7 can
 * aggregate per-procedure without re-grouping itself. {@link #engineVersion} (brief §54) lets a
 * later engine-semantics change be told apart from a content change when historical output is
 * replayed.
 */
public record RuleEvaluationBundle(
    UUID assessmentId,
    LocalDate evaluationDate,
    Map<String, List<RuleEvaluationResult>> resultsByTargetCode,
    Set<String> missingFacts,
    String engineVersion) {}
