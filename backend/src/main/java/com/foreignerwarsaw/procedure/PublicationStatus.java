package com.foreignerwarsaw.procedure;

/**
 * Shared lifecycle for every independently-published legal-content entity ({@code
 * ProcedureVersion}, {@code ThresholdVersion}) - deliberately just one status axis, not a separate
 * {@code VERIFIED} state here (brief §7): source verification is {@code
 * OfficialSource.verificationStatus}, a completely different axis from whether *our* legal-content
 * interpretation has been reviewed (brief §97 - a source can be VERIFIED while our content built
 * from it is still IN_REVIEW).
 */
public enum PublicationStatus {
  DRAFT,
  IN_REVIEW,
  APPROVED,
  PUBLISHED,
  ARCHIVED
}
