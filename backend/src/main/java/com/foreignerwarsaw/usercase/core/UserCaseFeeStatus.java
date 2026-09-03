package com.foreignerwarsaw.usercase.core;

/**
 * brief §15 - user-tracked, manual only; no payment gateway integration exists or is planned for
 * this phase (brief §152).
 */
public enum UserCaseFeeStatus {
  NOT_PAID,
  PAID,
  NOT_APPLICABLE,
  UNKNOWN
}
