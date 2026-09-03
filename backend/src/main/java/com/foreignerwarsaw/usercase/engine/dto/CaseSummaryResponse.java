package com.foreignerwarsaw.usercase.engine.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * brief §57 - the "My Cases" list row. Never the full snapshot (brief §57's "do not return entire
 * snapshot").
 */
public record CaseSummaryResponse(
    UUID id,
    String procedureCode,
    String procedureTitle,
    String status,
    int stepsCompleted,
    int stepsTotal,
    int documentsReady,
    int documentsTotal,
    boolean hasRequirementUpdates,
    Instant updatedAt) {}
