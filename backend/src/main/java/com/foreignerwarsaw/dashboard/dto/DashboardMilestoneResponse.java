package com.foreignerwarsaw.dashboard.dto;

import java.time.Instant;

/**
 * A real, already-occurred, positive-progress event - never a gamified score/level/XP (brief §23).
 */
public record DashboardMilestoneResponse(String label, Instant occurredAt) {}
