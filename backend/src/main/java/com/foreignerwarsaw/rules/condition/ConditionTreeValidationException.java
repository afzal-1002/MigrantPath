package com.foreignerwarsaw.rules.condition;

import java.util.List;

/**
 * Every semantic problem {@link ConditionTreeValidator} found in one condition tree, accumulated
 * rather than stopping at the first (brief §112: a rule author fixing one typo at a time against a
 * rejected draft is a poor editing loop) - {@link #getProblems()} lists them all.
 */
public class ConditionTreeValidationException extends RuntimeException {

  private final List<String> problems;

  public ConditionTreeValidationException(List<String> problems) {
    super("Condition tree failed validation: " + String.join("; ", problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> getProblems() {
    return problems;
  }
}
