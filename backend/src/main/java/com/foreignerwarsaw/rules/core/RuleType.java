package com.foreignerwarsaw.rules.core;

/**
 * The conceptual purpose of a {@link Rule} (brief §7) - never conflated with a Phase 7
 * recommendation ranking (`PRIMARY_MATCH`/...), which is a judgment about the *outcome* of one or
 * more rules, not a rule's own declared purpose.
 */
public enum RuleType {
  /** "Do the known facts satisfy this legal path?" */
  ELIGIBILITY,
  /** "Does this procedure/requirement even apply to this person's situation?" */
  APPLICABILITY,
  /** Satisfied conditions here make the target NOT applicable. */
  EXCLUSION,
  REQUIREMENT,
  INFORMATION_REQUIRED
}
