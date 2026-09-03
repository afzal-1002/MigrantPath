package com.foreignerwarsaw.procedure.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateProcedureVersionRequest(
    @NotBlank String title,
    String summary,
    String description,
    LocalDate effectiveFrom,
    String changeSummary) {}
