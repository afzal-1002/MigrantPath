package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOfficialSourceRequest(
    @NotBlank String title, @NotBlank String sourceUrl, @NotNull SourceType sourceType) {}
