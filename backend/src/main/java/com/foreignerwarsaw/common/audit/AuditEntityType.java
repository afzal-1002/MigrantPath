package com.foreignerwarsaw.common.audit;

/** What kind of thing an {@link AuditLog} row or {@code AdminReview} row is about. */
public enum AuditEntityType {
  PROCEDURE,
  PROCEDURE_VERSION,
  RULE,
  RULE_VERSION,
  THRESHOLD,
  THRESHOLD_VERSION,
  QUESTIONNAIRE,
  QUESTIONNAIRE_VERSION,
  OFFICIAL_SOURCE,
  USER
}
