package com.foreignerwarsaw.procedure.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDraftVersionRequest(
    @NotBlank String title, String summary, String description) {}
