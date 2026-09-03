package com.foreignerwarsaw.rules.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleDraftVersionRequest(
    @NotBlank String conditionTree, String explanationKey) {}
