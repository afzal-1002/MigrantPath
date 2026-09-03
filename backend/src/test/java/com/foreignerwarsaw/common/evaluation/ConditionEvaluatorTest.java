package com.foreignerwarsaw.common.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * One test per {@link ComparisonOperator} (brief §79), plus a couple of "actual value absent" cases
 * proving EXISTS-family operators degrade correctly for a hidden/unanswered question.
 */
class ConditionEvaluatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private JsonNode json(String json) throws Exception {
    return objectMapper.readTree(json);
  }

  @Test
  void equals_matchesSameTypedValue() throws Exception {
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.EQUALS, true, json("true"))).isTrue();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.EQUALS, "MARRIED", json("\"MARRIED\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.EQUALS, "SINGLE", json("\"MARRIED\"")))
        .isFalse();
  }

  @Test
  void notEquals_isTrueWhenActualValueIsAbsent() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.NOT_EQUALS, null, json("\"MARRIED\"")))
        .isTrue();
  }

  @Test
  void in_matchesAnyArrayElement() throws Exception {
    JsonNode array = json("[\"A\",\"B\",\"C\"]");
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.IN, "B", array)).isTrue();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.IN, "Z", array)).isFalse();
  }

  @Test
  void notIn_isTrueWhenValueAbsentFromArray() throws Exception {
    JsonNode array = json("[\"A\",\"B\"]");
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.NOT_IN, "Z", array)).isTrue();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.NOT_IN, "A", array)).isFalse();
  }

  @Test
  void contains_checksMultiSelectCollectionMembership() throws Exception {
    Set<String> selected = Set.of("WORK", "STUDY");
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.CONTAINS, selected, json("\"WORK\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.CONTAINS, selected, json("\"FAMILY\"")))
        .isFalse();
  }

  @Test
  void notContains_isTrueForEmptyOrNonMatchingCollection() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.NOT_CONTAINS, List.of(), json("\"WORK\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.NOT_CONTAINS, Set.of("STUDY"), json("\"WORK\"")))
        .isTrue();
  }

  @Test
  void exists_isFalseForNullOrEmptyCollection() {
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.EXISTS, null, null)).isFalse();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.EXISTS, List.of(), null)).isFalse();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.EXISTS, "PK", null)).isTrue();
  }

  @Test
  void notExists_isTrueOnlyWhenAbsent() {
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.NOT_EXISTS, null, null)).isTrue();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.NOT_EXISTS, "PK", null)).isFalse();
  }

  @Test
  void greaterThan_comparesDecimalNumerically_neverAsStrings() throws Exception {
    // "15000" > "9000" would be false as a string comparison - this must use BigDecimal.
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.GREATER_THAN, new BigDecimal("15000"), json("9000")))
        .isTrue();
  }

  @Test
  void greaterThanOrEqual_includesEqualValue() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("9000"), json("9000")))
        .isTrue();
  }

  @Test
  void lessThan_comparesIntegerNumerically() throws Exception {
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.LESS_THAN, 3L, json("5"))).isTrue();
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.LESS_THAN, 7L, json("5"))).isFalse();
  }

  @Test
  void lessThanOrEqual_includesEqualValue() throws Exception {
    assertThat(ConditionEvaluator.evaluate(ComparisonOperator.LESS_THAN_OR_EQUAL, 5L, json("5")))
        .isTrue();
  }

  @Test
  void dateBefore_comparesAsDates() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_BEFORE, LocalDate.of(2020, 1, 1), json("\"2025-01-01\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_BEFORE, LocalDate.of(2030, 1, 1), json("\"2025-01-01\"")))
        .isFalse();
  }

  @Test
  void dateAfter_comparesAsDates() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_AFTER, LocalDate.of(2030, 1, 1), json("\"2025-01-01\"")))
        .isTrue();
  }

  @Test
  void dateBeforeOrEqual_includesEqualDate() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_BEFORE_OR_EQUAL,
                LocalDate.of(2025, 1, 1),
                json("\"2025-01-01\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_BEFORE_OR_EQUAL,
                LocalDate.of(2025, 1, 2),
                json("\"2025-01-01\"")))
        .isFalse();
  }

  @Test
  void dateAfterOrEqual_includesEqualDate() throws Exception {
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_AFTER_OR_EQUAL,
                LocalDate.of(2025, 1, 1),
                json("\"2025-01-01\"")))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(
                ComparisonOperator.DATE_AFTER_OR_EQUAL,
                LocalDate.of(2024, 12, 31),
                json("\"2025-01-01\"")))
        .isFalse();
  }

  @Test
  void between_isInclusiveOfBothBounds() throws Exception {
    JsonNode range = json("[9000, 15000]");
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.BETWEEN, new BigDecimal("9000"), range))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.BETWEEN, new BigDecimal("15000"), range))
        .isTrue();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.BETWEEN, new BigDecimal("8999"), range))
        .isFalse();
    assertThat(
            ConditionEvaluator.evaluate(ComparisonOperator.BETWEEN, new BigDecimal("15001"), range))
        .isFalse();
  }

  @Test
  void between_rejectsAnArrayThatIsNotExactlyTwoElements() throws Exception {
    JsonNode malformed = json("[1, 2, 3]");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ConditionEvaluator.evaluate(
                    ComparisonOperator.BETWEEN, new BigDecimal("2"), malformed))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isMemberOfCountryGroup_neverReachesThisPureEvaluator() {
    // Brief §41/§68: this operator needs a live CountryClassificationService call -
    // com.foreignerwarsaw.rules.evaluation.RuleEvaluator must intercept it first.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ConditionEvaluator.evaluate(
                    ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP, "DE", null))
        .isInstanceOf(IllegalStateException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                ConditionEvaluator.evaluate(
                    ComparisonOperator.IS_NOT_MEMBER_OF_COUNTRY_GROUP, "DE", null))
        .isInstanceOf(IllegalStateException.class);
  }
}
