package com.foreignerwarsaw.common.audit;

/**
 * Every administrative mutation Phase 9 records (brief §61) - named for the actual domain action
 * taken, never a generic {@code CREATE}/{@code UPDATE}/{@code DELETE} (brief §80's "domain actions,
 * not table CRUD" applies to the audit vocabulary too, not just the API surface).
 */
public enum AuditActionType {
  PROCEDURE_CREATED,
  PROCEDURE_VERSION_CREATED,
  PROCEDURE_VERSION_UPDATED,
  PROCEDURE_STEP_ADDED,
  PROCEDURE_STEP_UPDATED,
  PROCEDURE_STEP_REMOVED,
  PROCEDURE_DOCUMENT_ADDED,
  PROCEDURE_DOCUMENT_UPDATED,
  PROCEDURE_DOCUMENT_REMOVED,
  PROCEDURE_FEE_ADDED,
  PROCEDURE_FEE_UPDATED,
  PROCEDURE_FEE_REMOVED,

  RULE_CREATED,
  RULE_VERSION_CREATED,
  RULE_VERSION_UPDATED,

  THRESHOLD_CREATED,
  THRESHOLD_VERSION_CREATED,
  THRESHOLD_VERSION_UPDATED,

  QUESTIONNAIRE_VERSION_CREATED,

  CONTENT_SUBMITTED,
  CONTENT_APPROVED,
  CONTENT_CHANGES_REQUESTED,
  CONTENT_REJECTED,
  CONTENT_SENT_BACK_TO_DRAFT,
  CONTENT_PUBLISHED,
  CONTENT_ARCHIVED,

  SOURCE_CREATED,
  SOURCE_VERIFIED,
  SOURCE_MARKED_OUTDATED,

  ROLE_ASSIGNED,
  ROLE_REMOVED,

  // Phase 12 (Security/Privacy/GDPR) - self-service account privacy actions. Metadata for these
  // never carries email/name/answer content (see AccountDeletionService/AccountExportService) -
  // only a privacy-safe internal reference, per this project's own audit-vocabulary discipline
  // above applied to the privacy domain.
  ACCOUNT_DELETION_REQUESTED,
  ACCOUNT_DELETION_COMPLETED,
  PERSONAL_DATA_EXPORT_REQUESTED,
  PERSONAL_DATA_EXPORT_COMPLETED
}
