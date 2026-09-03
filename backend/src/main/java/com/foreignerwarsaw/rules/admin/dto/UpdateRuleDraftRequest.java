package com.foreignerwarsaw.rules.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRuleDraftRequest(
    @NotBlank String conditionTree, String explanationKey, String changeSummary) {}
