package com.foreignerwarsaw.user;

/**
 * Mirrors the CHECK constraint on {@code users.status} (V1__create_users.sql) - see
 * docs/database/DATABASE.md §1.
 *
 * <p>{@code DISABLED} and {@code DELETED} are reserved for later phases (administrative disable,
 * GDPR erasure - Phase 12) and are not reachable through any Phase 2 code path; they exist now so
 * the schema doesn't need a later migration to widen the CHECK constraint.
 */
public enum UserStatus {
  PENDING_VERIFICATION,
  ACTIVE,
  LOCKED,
  DISABLED,
  DELETED
}
