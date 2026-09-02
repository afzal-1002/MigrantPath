package com.foreignerwarsaw.questionnaire.assessment;

/**
 * brief §23. {@code ABANDONED} is a reserved terminal status the schema supports but no Phase 5
 * code path sets yet (no auto-expiry job exists) - see PHASE_5_REPORT.md "Known Issues." {@code
 * SUPERSEDED} is what an explicit restart (brief §35) or "update answers on a completed assessment"
 * (brief §36) sets on the row being replaced.
 */
public enum AssessmentStatus {
  IN_PROGRESS,
  COMPLETED,
  ABANDONED,
  SUPERSEDED
}
