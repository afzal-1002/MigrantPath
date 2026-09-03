package com.foreignerwarsaw.usercase.engine;

/**
 * Two transparent counts, never one blended percentage (brief §19/§20 - "avoid arbitrary hidden
 * weighting"). Denominators exclude {@code NOT_APPLICABLE} and non-mandatory (informational) items;
 * {@code CONDITIONAL} documents are tracked separately, not folded into either count (brief §87/§88
 * - a document nobody has confirmed applies to this user shouldn't silently count against
 * "documents ready").
 */
public record CaseProgress(
    int stepsCompleted,
    int stepsTotal,
    int documentsReady,
    int documentsTotal,
    int conditionalDocumentsToReview) {}
