package com.foreignerwarsaw.recommendation.core;

/**
 * The whole-run outcome (brief §38) - distinct from any single {@link RecommendationType}, which is
 * per-procedure. {@code PARTIAL} is deliberate (brief §49): one procedure hitting a Rule {@code
 * ERROR} does not invalidate every other procedure's successfully-computed recommendation.
 */
public enum RecommendationRunStatus {
  RUNNING,
  COMPLETED,
  PARTIAL,
  FAILED
}
