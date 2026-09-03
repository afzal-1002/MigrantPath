package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.questionnaire.question.Question;
import com.foreignerwarsaw.questionnaire.question.QuestionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The central catalogue of facts a {@code RuleVersion.conditionTree} may reference (brief §12) - a
 * rule publishing an unknown fact, or a fact/operator combination that doesn't make sense for that
 * fact's type, must fail validation before it can ever reach production evaluation (brief §12,
 * §23's "all referenced facts known" / "operators compatible with fact types").
 *
 * <p><b>Direct facts</b> (brief §13) are exactly Phase 5's {@link Question} rows, keyed by {@code
 * Question.code} - this registry never duplicates that list, it reads it. <b>Derived facts</b>
 * (brief §13) are the small, explicitly-named, deterministic set below - {@code AGE_YEARS} (from
 * {@code DATE_OF_BIRTH}), {@code IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP} and {@code
 * COUNTRY_GROUP_MEMBERSHIPS} (from {@code CITIZENSHIP_COUNTRY}). Deliberately never a legal
 * conclusion (brief §13's explicit "do not derive IS_BLUE_CARD_ELIGIBLE inside a FactProvider" -
 * that's what a whole {@code RuleVersion} exists to decide, never a fact resolver shortcut).
 */
@Service
public class FactRegistry {

  /**
   * Derived facts this codebase actually computes (see {@link FactResolver}) - kept deliberately
   * small; add an entry here only alongside real resolution logic for it, never speculatively.
   */
  private static final Map<String, FactDefinition> DERIVED_FACTS =
      Map.of(
          "AGE_YEARS",
              new FactDefinition("AGE_YEARS", QuestionType.INTEGER, true, numericOperators()),
          "IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP",
              new FactDefinition(
                  "IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP",
                  QuestionType.BOOLEAN,
                  true,
                  booleanOperators()),
          "COUNTRY_GROUP_MEMBERSHIPS",
              new FactDefinition(
                  "COUNTRY_GROUP_MEMBERSHIPS",
                  QuestionType.MULTI_SELECT,
                  true,
                  multiSelectOperators()));

  private final QuestionRepository questionRepository;

  public FactRegistry(QuestionRepository questionRepository) {
    this.questionRepository = questionRepository;
  }

  @Transactional(readOnly = true)
  public Optional<FactDefinition> find(String factCode) {
    FactDefinition derived = DERIVED_FACTS.get(factCode);
    if (derived != null) {
      return Optional.of(derived);
    }
    return questionRepository.findByCode(factCode).map(FactRegistry::fromQuestion);
  }

  /**
   * Every fact a Phase 9 admin condition builder may offer in its fact dropdown (brief §38) -
   * derived facts plus every direct fact (Question), so an author never has to know a fact code by
   * heart or type raw JSON.
   */
  @Transactional(readOnly = true)
  public java.util.List<FactDefinition> listAll() {
    java.util.List<FactDefinition> all = new java.util.ArrayList<>(DERIVED_FACTS.values());
    questionRepository.findAll().stream().map(FactRegistry::fromQuestion).forEach(all::add);
    return all;
  }

  private static FactDefinition fromQuestion(Question question) {
    return new FactDefinition(
        question.getCode(),
        question.getQuestionType(),
        false,
        operatorsFor(question.getQuestionType()));
  }

  private static Set<ComparisonOperator> operatorsFor(QuestionType type) {
    return switch (type) {
      case BOOLEAN -> booleanOperators();
      case INTEGER, DECIMAL -> numericOperators();
      case DATE -> dateOperators();
      case MULTI_SELECT -> multiSelectOperators();
      case COUNTRY -> countryOperators();
      case SINGLE_SELECT, TEXT, REGION, CITY, DISTRICT -> stringOperators();
    };
  }

  private static Set<ComparisonOperator> booleanOperators() {
    return Set.of(
        ComparisonOperator.EQUALS,
        ComparisonOperator.NOT_EQUALS,
        ComparisonOperator.EXISTS,
        ComparisonOperator.NOT_EXISTS);
  }

  private static Set<ComparisonOperator> numericOperators() {
    return Set.of(
        ComparisonOperator.EQUALS,
        ComparisonOperator.NOT_EQUALS,
        ComparisonOperator.GREATER_THAN,
        ComparisonOperator.GREATER_THAN_OR_EQUAL,
        ComparisonOperator.LESS_THAN,
        ComparisonOperator.LESS_THAN_OR_EQUAL,
        ComparisonOperator.BETWEEN,
        ComparisonOperator.EXISTS,
        ComparisonOperator.NOT_EXISTS);
  }

  private static Set<ComparisonOperator> dateOperators() {
    return Set.of(
        ComparisonOperator.EQUALS,
        ComparisonOperator.NOT_EQUALS,
        ComparisonOperator.DATE_BEFORE,
        ComparisonOperator.DATE_BEFORE_OR_EQUAL,
        ComparisonOperator.DATE_AFTER,
        ComparisonOperator.DATE_AFTER_OR_EQUAL,
        ComparisonOperator.EXISTS,
        ComparisonOperator.NOT_EXISTS);
  }

  private static Set<ComparisonOperator> multiSelectOperators() {
    return Set.of(
        ComparisonOperator.CONTAINS,
        ComparisonOperator.NOT_CONTAINS,
        ComparisonOperator.EXISTS,
        ComparisonOperator.NOT_EXISTS);
  }

  private static Set<ComparisonOperator> stringOperators() {
    return Set.of(
        ComparisonOperator.EQUALS,
        ComparisonOperator.NOT_EQUALS,
        ComparisonOperator.IN,
        ComparisonOperator.NOT_IN,
        ComparisonOperator.EXISTS,
        ComparisonOperator.NOT_EXISTS);
  }

  private static Set<ComparisonOperator> countryOperators() {
    Set<ComparisonOperator> operators = new HashSet<>(stringOperators());
    operators.add(ComparisonOperator.IS_MEMBER_OF_COUNTRY_GROUP);
    operators.add(ComparisonOperator.IS_NOT_MEMBER_OF_COUNTRY_GROUP);
    return Set.copyOf(operators);
  }
}
