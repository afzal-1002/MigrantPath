package com.foreignerwarsaw.usercase.core;

/**
 * brief §9. {@code NOT_APPLICABLE}/{@code BLOCKED} exist in the schema for a future
 * conditional-step engine (brief §10/§72) but are never set by Phase 8 itself - every snapshotted
 * step starts {@code NOT_STARTED} (no conditional-step evaluation exists yet, see
 * docs/cases/USER_CASE_MODEL.md).
 */
public enum UserCaseStepStatus {
  NOT_STARTED,
  IN_PROGRESS,
  COMPLETED,
  SKIPPED,
  BLOCKED,
  NOT_APPLICABLE
}
