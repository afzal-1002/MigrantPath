package com.foreignerwarsaw.usercase.core;

/**
 * Baseline case-lifecycle status (brief §3/§22) - a general state machine every procedure type
 * shares in Phase 8; not every case necessarily passes through every status (brief: "do not assume
 * every procedure uses every status"). Allowed transitions live in {@link
 * UserCaseStatusTransitions}, documented in full in docs/cases/CASE_STATUS_WORKFLOW.md.
 */
public enum UserCaseStatus {
  DRAFT,
  PREPARING,
  READY_TO_SUBMIT,
  SUBMITTED,
  WAITING,
  ADDITIONAL_DOCUMENTS_REQUIRED,
  DECISION_RECEIVED,
  APPROVED,
  REJECTED,
  APPEAL,
  COMPLETED,
  CANCELLED
}
