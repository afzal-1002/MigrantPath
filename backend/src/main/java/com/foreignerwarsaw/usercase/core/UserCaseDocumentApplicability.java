package com.foreignerwarsaw.usercase.core;

/**
 * Structural relevance (brief §12/§14) - never conflated with {@link UserCaseDocumentStatus}'s
 * checklist progress. Derived once, at snapshot time, directly from the source {@code
 * DocumentRequirementVersion.requirementType} (Phase 4) - {@code DEFAULT_REQUIRED}/{@code
 * INFORMATIONAL} become {@link #APPLICABLE}, {@code CONDITIONAL} becomes {@link
 * #NEEDS_CONFIRMATION} (brief §13/§74: "do not automatically mark NOT_APPLICABLE" for something no
 * rule has actually evaluated). {@link #NOT_APPLICABLE} is never produced by Phase 8 itself -
 * reserved for a future Phase 6 {@code DOCUMENT_REQUIREMENT}-target rule evaluation
 * (docs/cases/USER_CASE_MODEL.md's "Personalization" section explains why that doesn't exist yet).
 */
public enum UserCaseDocumentApplicability {
  APPLICABLE,
  NEEDS_CONFIRMATION,
  NOT_APPLICABLE
}
