package com.foreignerwarsaw.dashboard.dto;

import java.time.Instant;

/**
 * A user-friendly mapping of a real {@code UserCaseEvent} (brief §22/§56) - never a raw backend
 * event-type string.
 */
public record DashboardActivityResponse(String label, Instant occurredAt) {}
