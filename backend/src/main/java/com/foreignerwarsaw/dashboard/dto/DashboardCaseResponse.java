package com.foreignerwarsaw.dashboard.dto;

import com.foreignerwarsaw.procedure.core.dto.ProcedureAuthorityRefResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureOfficeRefResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The dashboard hero's case-progress/case-info panels. {@code stageIndex}/{@code stageLabel} are a
 * deterministic, real mapping of {@code UserCaseStatus} onto a 5-stage timeline (DashboardService's
 * own documented mapping) - never a claim that the application has been submitted to a real
 * authority unless the case's own real status says so.
 */
public record DashboardCaseResponse(
    UUID id,
    String procedureCode,
    String procedureTitle,
    String status,
    Instant startedAt,
    long daysActive,
    int stepsCompleted,
    int stepsTotal,
    int documentsCompleted,
    int documentsTotal,
    int feesCompleted,
    int feesTotal,
    boolean hasRequirementUpdates,
    int stageIndex,
    String stageLabel,
    List<ProcedureAuthorityRefResponse> authorities,
    List<ProcedureOfficeRefResponse> offices) {}
