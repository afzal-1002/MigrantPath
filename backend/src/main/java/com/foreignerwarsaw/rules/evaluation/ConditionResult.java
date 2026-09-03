package com.foreignerwarsaw.rules.evaluation;

/**
 * The outcome of one condition node (leaf or combined, brief §30) - a closed four-state result,
 * never a plain boolean. {@code MISSING} is the deliberate third state that keeps "the user hasn't
 * told us yet" from ever being silently treated as "no" (brief §29): a Blue Card salary condition
 * with no salary answer is {@code MISSING}, never {@code FAIL}. {@code ERROR} means the rule
 * configuration itself is broken (unknown fact/threshold at runtime, a comparison against the wrong
 * type) - never conflated with a legitimate {@code FAIL} (brief §64/§118).
 */
public enum ConditionResult {
  PASS,
  FAIL,
  MISSING,
  ERROR
}
