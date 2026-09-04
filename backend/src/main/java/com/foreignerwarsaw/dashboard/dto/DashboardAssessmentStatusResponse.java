package com.foreignerwarsaw.dashboard.dto;

import java.util.UUID;

public record DashboardAssessmentStatusResponse(
    UUID assessmentId, String status, int progressPercent) {}
