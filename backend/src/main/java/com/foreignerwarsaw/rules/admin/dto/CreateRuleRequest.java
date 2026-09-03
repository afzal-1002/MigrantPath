package com.foreignerwarsaw.rules.admin.dto;

import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRuleRequest(
    @NotBlank String code,
    @NotBlank String canonicalName,
    @NotNull RuleType ruleType,
    @NotNull RuleTargetType targetType,
    @NotBlank String targetCode) {}
