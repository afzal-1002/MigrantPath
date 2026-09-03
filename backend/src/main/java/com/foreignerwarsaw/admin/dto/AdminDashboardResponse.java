package com.foreignerwarsaw.admin.dto;

/**
 * Operational summary only (brief §16) - never vanity statistics. Every field is a count an editor,
 * reviewer, or admin should act on: drafts waiting to be finished, items waiting for review, items
 * approved and ready to publish, and sources that need attention.
 */
public record AdminDashboardResponse(
    long draftProcedureVersions,
    long draftRuleVersions,
    long draftThresholdVersions,
    long draftQuestionnaireVersions,
    long pendingReviews,
    long approvedProcedureVersionsAwaitingPublication,
    long approvedRuleVersionsAwaitingPublication,
    long approvedThresholdVersionsAwaitingPublication,
    long approvedQuestionnaireVersionsAwaitingPublication,
    long sourcesNeedingReview,
    long outdatedSources) {}
