package com.foreignerwarsaw.rules.condition;

/**
 * A {@code RuleVersion.conditionTree} that isn't even structurally well-formed (brief §66) -
 * unknown node shape, unknown operator name, or excessive nesting depth. Purely structural;
 * semantic problems (unknown fact/threshold/country group) are {@code ConditionTreeValidator}'s
 * job, one layer up, since those need database access this parser deliberately doesn't have.
 */
public class ConditionTreeParseException extends RuntimeException {

  public ConditionTreeParseException(String message) {
    super(message);
  }
}
