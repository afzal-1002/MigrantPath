package com.foreignerwarsaw.rules.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exactly which {@code ThresholdVersion} was resolved for one {@code "threshold"} reference during
 * this evaluation (brief §51) - reproducibility requires knowing not just "the salary threshold"
 * but the precise version/value/effective date used.
 */
public record ThresholdUsage(
    String thresholdCode, UUID thresholdVersionId, BigDecimal value, LocalDate effectiveFrom) {}
