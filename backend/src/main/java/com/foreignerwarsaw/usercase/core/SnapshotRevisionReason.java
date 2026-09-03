package com.foreignerwarsaw.usercase.core;

/**
 * Why a {@link UserCaseSnapshotRevision} exists - {@code INITIAL} for case creation, {@code
 * UPGRADE} for an explicit {@code POST .../upgrade} (brief §31/§32) - never automatic.
 */
public enum SnapshotRevisionReason {
  INITIAL,
  UPGRADE
}
