package com.foreignerwarsaw.procedure.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PublishRequest(@NotNull LocalDate effectiveFrom) {}
