package com.foreignerwarsaw.rules.admin.dto;

import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RuleVersion;

public record AdminRuleSummaryResponse(
    String code,
    String canonicalName,
    String ruleType,
    String targetType,
    String targetCode,
    boolean active,
    Integer activeVersionNumber,
    Integer latestVersionNumber,
    String latestVersionStatus) {

  public static AdminRuleSummaryResponse from(Rule rule, RuleVersion active, RuleVersion latest) {
    return new AdminRuleSummaryResponse(
        rule.getCode(),
        rule.getCanonicalName(),
        rule.getRuleType().name(),
        rule.getTargetType().name(),
        rule.getTargetCode(),
        rule.isActive(),
        active != null ? active.getVersionNumber() : null,
        latest != null ? latest.getVersionNumber() : null,
        latest != null ? latest.getStatus().name() : null);
  }
}
