package com.foreignerwarsaw.rules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.Map;

/**
 * A rule-authoring dry run against synthetic, admin-typed facts (brief §43/§113) - never a real
 * user's Assessment. {@code facts} keys are fact codes (see {@code GET /api/v1/admin/facts}),
 * values are plain JSON scalars/arrays the caller believes match that fact's type; {@link
 * com.foreignerwarsaw.rules.evaluation.RuleEvaluator} resolves and traces them exactly as it would
 * a real assessment's answers.
 */
public record RuleDryRunRequest(
    @NotBlank String conditionTree,
    String explanationKey,
    Map<String, Object> facts,
    LocalDate evaluationDate) {}
