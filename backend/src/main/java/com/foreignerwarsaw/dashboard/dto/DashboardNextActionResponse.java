package com.foreignerwarsaw.dashboard.dto;

import java.util.UUID;

/**
 * {@code severity} is one of {@code ATTENTION}/{@code NEXT}/{@code INFO} (never a raw enum name
 * shown to the user - the frontend maps each to its own color/label, brief §16/§50). {@code
 * caseId}/{@code assessmentId} are whichever one applies (at most one non-null) so the frontend can
 * build the right route without the backend embedding a URL.
 */
public record DashboardNextActionResponse(
    String heading,
    String detail,
    String severity,
    String ctaLabel,
    UUID caseId,
    UUID assessmentId) {}
