package com.foreignerwarsaw.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import com.foreignerwarsaw.reference.country.CountryGroup;
import com.foreignerwarsaw.reference.country.CountryGroupRepository;
import com.foreignerwarsaw.rules.evaluation.FactDefinition;
import com.foreignerwarsaw.rules.evaluation.FactRegistry;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Semantic condition-tree validation (brief §23/§112) - every problem type accumulated rather than
 * short-circuiting on the first, isolated with mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class ConditionTreeValidatorTest {

  @Mock private FactRegistry factRegistry;
  @Mock private ThresholdRepository thresholdRepository;
  @Mock private CountryGroupRepository countryGroupRepository;

  private ConditionTreeValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new ConditionTreeValidator(
            factRegistry, thresholdRepository, countryGroupRepository, new ObjectMapper());
  }

  private FactDefinition numericFact(String code) {
    return new FactDefinition(
        code,
        QuestionType.INTEGER,
        false,
        Set.of(
            ComparisonOperator.EQUALS,
            ComparisonOperator.GREATER_THAN,
            ComparisonOperator.EXISTS,
            ComparisonOperator.NOT_EXISTS));
  }

  private FactDefinition countryFact(String code) {
    return new FactDefinition(
        code,
        QuestionType.COUNTRY,
        false,
        Set.of(
            ComparisonOperator.EQUALS,
            ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP,
            ComparisonOperator.IS_NOT_MEMBER_OF_COUNTRY_GROUP));
  }

  @Test
  void rejectsAnUnknownFact() {
    when(factRegistry.find("UNKNOWN_FACT")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> validator.validate("{\"fact\":\"UNKNOWN_FACT\",\"operator\":\"EXISTS\"}"))
        .isInstanceOf(ConditionTreeValidationException.class)
        .satisfies(
            e ->
                assertThat(((ConditionTreeValidationException) e).getProblems())
                    .anyMatch(p -> p.contains("unknown fact")));
  }

  @Test
  void rejectsAnOperatorNotValidForTheFactType() {
    when(factRegistry.find("AGE_YEARS")).thenReturn(Optional.of(numericFact("AGE_YEARS")));

    assertThatThrownBy(
            () ->
                validator.validate(
                    "{\"fact\":\"AGE_YEARS\",\"operator\":\"CONTAINS\",\"value\":\"X\"}"))
        .isInstanceOf(ConditionTreeValidationException.class)
        .satisfies(
            e ->
                assertThat(((ConditionTreeValidationException) e).getProblems())
                    .anyMatch(p -> p.contains("not valid for fact")));
  }

  @Test
  void rejectsAnUnknownThresholdCode() {
    when(factRegistry.find("SALARY_MONTHLY_GROSS"))
        .thenReturn(Optional.of(numericFact("SALARY_MONTHLY_GROSS")));
    when(thresholdRepository.findByCodeIgnoreCase("NO_SUCH_THRESHOLD"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                validator.validate(
                    "{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN\",\"threshold\":\"NO_SUCH_THRESHOLD\"}"))
        .isInstanceOf(ConditionTreeValidationException.class)
        .satisfies(
            e ->
                assertThat(((ConditionTreeValidationException) e).getProblems())
                    .anyMatch(p -> p.contains("unknown threshold code")));
  }

  @Test
  void acceptsAKnownThresholdCode() {
    when(factRegistry.find("SALARY_MONTHLY_GROSS"))
        .thenReturn(Optional.of(numericFact("SALARY_MONTHLY_GROSS")));
    when(thresholdRepository.findByCodeIgnoreCase("BLUE_CARD_SALARY_THRESHOLD"))
        .thenReturn(Optional.of(mock(Threshold.class)));

    validator.validate(
        "{\"fact\":\"SALARY_MONTHLY_GROSS\",\"operator\":\"GREATER_THAN\",\"threshold\":\"BLUE_CARD_SALARY_THRESHOLD\"}");
    // no exception
  }

  @Test
  void rejectsAnUnknownCountryGroupCode() {
    when(factRegistry.find("CITIZENSHIP_COUNTRY"))
        .thenReturn(Optional.of(countryFact("CITIZENSHIP_COUNTRY")));
    when(countryGroupRepository.findByCode("NOT_A_GROUP")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                validator.validate(
                    "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"IS_MEMBER_OF_COUNTRY_GROUP\",\"value\":\"NOT_A_GROUP\"}"))
        .isInstanceOf(ConditionTreeValidationException.class)
        .satisfies(
            e ->
                assertThat(((ConditionTreeValidationException) e).getProblems())
                    .anyMatch(p -> p.contains("unknown country group code")));
  }

  @Test
  void acceptsAKnownCountryGroupCode() {
    when(factRegistry.find("CITIZENSHIP_COUNTRY"))
        .thenReturn(Optional.of(countryFact("CITIZENSHIP_COUNTRY")));
    when(countryGroupRepository.findByCode("EU_MEMBER"))
        .thenReturn(Optional.of(mock(CountryGroup.class)));

    validator.validate(
        "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"IS_MEMBER_OF_COUNTRY_GROUP\",\"value\":\"EU_MEMBER\"}");
    // no exception
  }

  @Test
  void accumulatesMultipleProblemsInOneNestedTreeRatherThanStoppingAtTheFirst() {
    when(factRegistry.find(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                validator.validate(
                    "{\"all\":[{\"fact\":\"UNKNOWN_A\",\"operator\":\"EXISTS\"},{\"fact\":\"UNKNOWN_B\",\"operator\":\"EXISTS\"}]}"))
        .isInstanceOf(ConditionTreeValidationException.class)
        .satisfies(
            e -> assertThat(((ConditionTreeValidationException) e).getProblems()).hasSize(2));
  }

  @Test
  void rejectsAMalformedTreeBeforeEverConsultingTheRegistries() {
    assertThatThrownBy(() -> validator.validate("{not json"))
        .isInstanceOf(ConditionTreeValidationException.class);
  }
}
