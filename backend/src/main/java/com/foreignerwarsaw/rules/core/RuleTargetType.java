package com.foreignerwarsaw.rules.core;

/**
 * What a {@link Rule} evaluates eligibility/applicability *for* (brief §6). Phase 6 only actually
 * exercises {@link #PROCEDURE}; the other values exist so a later target type needs no schema
 * change, not because this phase builds evaluation logic for them.
 */
public enum RuleTargetType {
  PROCEDURE,
  DOCUMENT_REQUIREMENT,
  STEP,
  FEE,
  THRESHOLD_APPLICABILITY,
  ROUTING
}
