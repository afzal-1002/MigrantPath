package com.foreignerwarsaw.dashboard.dto;

import java.time.Instant;

/**
 * Only real, already-recorded dates - never a computed/estimated immigration deadline (brief
 * §16/§20/§55 - mandatory legal-safety constraint). {@code type} is one of {@code
 * CASE_STARTED}/{@code CASE_SUBMITTED}/{@code CASE_COMPLETED}.
 */
public record DashboardDateResponse(String label, Instant date, String type) {}
