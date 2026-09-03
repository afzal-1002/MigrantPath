package com.foreignerwarsaw.rules.core;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Rule} identity lifecycle (docs/database/DATABASE.md §5) - mirrors {@code
 * ProcedureService}'s split between identity creation/lookup here and version content in {@link
 * RuleVersionService}.
 */
@Service
public class RuleService {

  private final RuleRepository ruleRepository;

  public RuleService(RuleRepository ruleRepository) {
    this.ruleRepository = ruleRepository;
  }

  @Transactional
  public Rule createRule(
      String code,
      String canonicalName,
      RuleType ruleType,
      RuleTargetType targetType,
      String targetCode) {
    if (ruleRepository.findByCodeIgnoreCase(code).isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "RULE_CODE_TAKEN", "Rule code already exists: " + code);
    }
    return ruleRepository.save(new Rule(code, canonicalName, ruleType, targetType, targetCode));
  }

  @Transactional(readOnly = true)
  public Rule getByCode(String code) {
    return ruleRepository
        .findByCodeIgnoreCase(code)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "No rule found for code " + code));
  }

  /**
   * Every active {@link Rule} targeting one {@code (targetType, targetCode)} pair (brief §38/§39's
   * "evaluate every rule that targets this procedure") - {@code active=false} rules (retired
   * without a code reuse) are deliberately excluded.
   */
  @Transactional(readOnly = true)
  public List<Rule> findActiveRulesForTarget(RuleTargetType targetType, String targetCode) {
    return ruleRepository.findByTargetTypeAndTargetCodeIgnoreCaseAndActiveTrue(
        targetType, targetCode);
  }
}
