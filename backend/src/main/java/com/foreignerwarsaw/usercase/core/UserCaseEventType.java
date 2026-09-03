package com.foreignerwarsaw.usercase.core;

/**
 * brief §24 - append-only case timeline (brief §25); never carries raw sensitive answer values
 * (brief §83), only stable codes/status transitions.
 */
public enum UserCaseEventType {
  CASE_CREATED,
  CASE_STATUS_CHANGED,
  STEP_COMPLETED,
  STEP_REOPENED,
  DOCUMENT_STATUS_CHANGED,
  FEE_STATUS_CHANGED,
  REQUIREMENTS_UPDATE_DETECTED,
  CASE_UPDATED_TO_NEW_VERSION,
  CASE_CANCELLED
}
