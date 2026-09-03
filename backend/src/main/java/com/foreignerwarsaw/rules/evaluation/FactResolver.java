package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.reference.country.CountryClassificationService;
import java.time.LocalDate;
import java.time.Period;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolves one fact's typed value for one evaluation (brief §43) - direct facts come straight off
 * {@link AssessmentFacts#answersByQuestionCode()} (already typed by Phase 5's {@code
 * AssessmentAnswer} columns); derived facts (brief §13/§44) are computed here, deterministically,
 * from {@code evaluationDate} and other direct facts - never from mutable state, never a database
 * write. Returns {@code null} for an absent/unresolvable fact - {@code RuleEvaluator} is what turns
 * that into a {@code MISSING} condition result, not this class.
 */
@Service
public class FactResolver {

  private final CountryClassificationService countryClassificationService;

  public FactResolver(CountryClassificationService countryClassificationService) {
    this.countryClassificationService = countryClassificationService;
  }

  public Object resolve(String factCode, AssessmentFacts facts, LocalDate evaluationDate) {
    return switch (factCode) {
      case "AGE_YEARS" -> ageYears(facts, evaluationDate);
      case "IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP" ->
          isOutsideFreeMovementGroup(facts, evaluationDate);
      case "COUNTRY_GROUP_MEMBERSHIPS" -> countryGroupMemberships(facts, evaluationDate);
      default -> facts.answersByQuestionCode().get(factCode);
    };
  }

  /**
   * {@code AGE_YEARS} at {@code evaluationDate}, derived from {@code DATE_OF_BIRTH} (brief §44) -
   * never persisted, always recomputed, correct across leap years/birthdays by construction ({@link
   * Period#getYears()} already handles both).
   */
  private Long ageYears(AssessmentFacts facts, LocalDate evaluationDate) {
    Object dateOfBirth = facts.answersByQuestionCode().get("DATE_OF_BIRTH");
    if (!(dateOfBirth instanceof LocalDate birthDate)) {
      return null;
    }
    return (long) Period.between(birthDate, evaluationDate).getYears();
  }

  private Boolean isOutsideFreeMovementGroup(AssessmentFacts facts, LocalDate evaluationDate) {
    Object citizenship = facts.answersByQuestionCode().get("CITIZENSHIP_COUNTRY");
    if (!(citizenship instanceof String countryCode)) {
      return null;
    }
    return countryClassificationService.isOutsideEuEeaSwissFreeMovementGroup(
        countryCode, evaluationDate);
  }

  private Set<String> countryGroupMemberships(AssessmentFacts facts, LocalDate evaluationDate) {
    Object citizenship = facts.answersByQuestionCode().get("CITIZENSHIP_COUNTRY");
    if (!(citizenship instanceof String countryCode)) {
      return null;
    }
    return Set.copyOf(countryClassificationService.classificationsFor(countryCode, evaluationDate));
  }
}
