package com.foreignerwarsaw.rules.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<Rule, UUID> {

  Optional<Rule> findByCodeIgnoreCase(String code);

  List<Rule> findByTargetTypeAndTargetCodeIgnoreCaseAndActiveTrue(
      RuleTargetType targetType, String targetCode);

  /**
   * Every active rule regardless of target (brief §38/§113's whole-assessment evaluation) - {@code
   * RuleEvaluationService#evaluateApplicableRules} groups the results by target itself.
   */
  List<Rule> findByActiveTrue();
}
