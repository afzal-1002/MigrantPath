package com.foreignerwarsaw.common.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Iterator;

/**
 * The one authoritative implementation of {@link ComparisonOperator} semantics (brief §71: "Typed
 * comparison only" - never {@code "15000" > "9000"} as strings). Shared by {@code
 * QuestionVisibilityService} (Phase 5) and, when it exists, Phase 6's rule matcher.
 *
 * <p>{@code actualValue} is always an already-typed Java value read from an {@code
 * AssessmentAnswer} - one of {@link String}, {@link Boolean}, {@link BigDecimal}, {@link Long},
 * {@link LocalDate}, or a {@link Collection}{@code <String>} (multi-select option codes). {@code
 * expectedValue} is the statically-configured {@code JsonNode} from {@code
 * QuestionDependency.expectedValue} - a JSON scalar for every operator except {@code IN}/{@code
 * NOT_IN} (a JSON array of allowed scalars) and {@code CONTAINS}/{@code NOT_CONTAINS} (a single
 * scalar checked against a collection {@code actualValue}).
 */
public final class ConditionEvaluator {

  private ConditionEvaluator() {}

  public static boolean evaluate(
      ComparisonOperator operator, Object actualValue, JsonNode expectedValue) {
    return switch (operator) {
      case EXISTS -> exists(actualValue);
      case NOT_EXISTS -> !exists(actualValue);
      case EQUALS -> exists(actualValue) && scalarEquals(actualValue, expectedValue);
      case NOT_EQUALS -> !exists(actualValue) || !scalarEquals(actualValue, expectedValue);
      case IN -> exists(actualValue) && anyElementEquals(expectedValue, actualValue);
      case NOT_IN -> !exists(actualValue) || !anyElementEquals(expectedValue, actualValue);
      case CONTAINS -> collectionContains(actualValue, expectedValue);
      case NOT_CONTAINS -> !collectionContains(actualValue, expectedValue);
      case GREATER_THAN -> exists(actualValue) && compareNumeric(actualValue, expectedValue) > 0;
      case GREATER_THAN_OR_EQUAL ->
          exists(actualValue) && compareNumeric(actualValue, expectedValue) >= 0;
      case LESS_THAN -> exists(actualValue) && compareNumeric(actualValue, expectedValue) < 0;
      case LESS_THAN_OR_EQUAL ->
          exists(actualValue) && compareNumeric(actualValue, expectedValue) <= 0;
      case BETWEEN -> exists(actualValue) && isBetween(actualValue, expectedValue);
      case DATE_BEFORE -> exists(actualValue) && compareDate(actualValue, expectedValue) < 0;
      case DATE_BEFORE_OR_EQUAL ->
          exists(actualValue) && compareDate(actualValue, expectedValue) <= 0;
      case DATE_AFTER -> exists(actualValue) && compareDate(actualValue, expectedValue) > 0;
      case DATE_AFTER_OR_EQUAL ->
          exists(actualValue) && compareDate(actualValue, expectedValue) >= 0;
      case IS_MEMBER_OF_COUNTRY_GROUP, IS_NOT_MEMBER_OF_COUNTRY_GROUP ->
          throw new IllegalStateException(
              operator
                  + " needs a live CountryClassificationService call - "
                  + "com.foreignerwarsaw.rules.evaluation.RuleEvaluator must intercept it before"
                  + " ever delegating here, never reach this pure evaluator.");
    };
  }

  /**
   * {@code expectedValue} is a two-element JSON array {@code [min, max]}, both bounds inclusive -
   * the same "typed, never string" comparison as every other numeric operator.
   */
  private static boolean isBetween(Object actualValue, JsonNode expectedValue) {
    if (!expectedValue.isArray() || expectedValue.size() != 2) {
      throw new IllegalArgumentException("BETWEEN expects a two-element [min, max] array");
    }
    return compareNumeric(actualValue, expectedValue.get(0)) >= 0
        && compareNumeric(actualValue, expectedValue.get(1)) <= 0;
  }

  private static boolean exists(Object actualValue) {
    if (actualValue == null) {
      return false;
    }
    if (actualValue instanceof Collection<?> collection) {
      return !collection.isEmpty();
    }
    if (actualValue instanceof String string) {
      return !string.isBlank();
    }
    return true;
  }

  private static boolean scalarEquals(Object actualValue, JsonNode expectedValue) {
    if (actualValue instanceof Boolean booleanValue) {
      return booleanValue == expectedValue.asBoolean();
    }
    if (actualValue instanceof BigDecimal || actualValue instanceof Long) {
      return compareNumeric(actualValue, expectedValue) == 0;
    }
    if (actualValue instanceof LocalDate) {
      return compareDate(actualValue, expectedValue) == 0;
    }
    return String.valueOf(actualValue).equals(expectedValue.asText());
  }

  private static boolean anyElementEquals(JsonNode expectedArray, Object actualValue) {
    if (!expectedArray.isArray()) {
      return scalarEquals(actualValue, expectedArray);
    }
    Iterator<JsonNode> elements = expectedArray.elements();
    while (elements.hasNext()) {
      if (scalarEquals(actualValue, elements.next())) {
        return true;
      }
    }
    return false;
  }

  private static boolean collectionContains(Object actualValue, JsonNode expectedValue) {
    if (!(actualValue instanceof Collection<?> collection) || collection.isEmpty()) {
      return false;
    }
    String expected = expectedValue.asText();
    return collection.stream().anyMatch(element -> String.valueOf(element).equals(expected));
  }

  private static int compareNumeric(Object actualValue, JsonNode expectedValue) {
    BigDecimal actual =
        switch (actualValue) {
          case BigDecimal decimal -> decimal;
          case Long longValue -> BigDecimal.valueOf(longValue);
          case Integer intValue -> BigDecimal.valueOf(intValue);
          case null, default ->
              throw new IllegalArgumentException(
                  "Cannot compare non-numeric value numerically: " + actualValue);
        };
    return actual.compareTo(expectedValue.decimalValue());
  }

  private static int compareDate(Object actualValue, JsonNode expectedValue) {
    if (!(actualValue instanceof LocalDate actual)) {
      throw new IllegalArgumentException("Cannot compare non-date value as a date: " + actualValue);
    }
    return actual.compareTo(LocalDate.parse(expectedValue.asText()));
  }
}
