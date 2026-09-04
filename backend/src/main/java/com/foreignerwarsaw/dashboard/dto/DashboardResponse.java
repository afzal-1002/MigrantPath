package com.foreignerwarsaw.dashboard.dto;

import com.foreignerwarsaw.recommendation.engine.dto.RecommendationResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseSummaryResponse;
import java.util.List;

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - the single, purpose-built dashboard summary ({@code
 * GET /api/v1/dashboard}), current authenticated user only (never an arbitrary userId - see {@code
 * DashboardController}). Composes existing read services (brief §32/§67/§68) - no
 * Rules/Recommendation/UserCase state-machine logic lives here, only aggregation and user-safe
 * summarization. Deliberately excludes {@code AssessmentAnswer} values and any questionnaire fact
 * (brief §27/§31/§57) - this is a summary of outcomes and progress, never the underlying sensitive
 * answers.
 */
public record DashboardResponse(
    DashboardProfileResponse profile,
    DashboardCaseResponse primaryCase,
    List<CaseSummaryResponse> activeCases,
    List<DashboardNextActionResponse> nextActions,
    List<DashboardDateResponse> importantDates,
    List<DashboardMilestoneResponse> completedMilestones,
    List<RecommendationResponse> latestRecommendations,
    List<DashboardActivityResponse> recentActivity,
    DashboardAssessmentStatusResponse assessmentStatus) {}
