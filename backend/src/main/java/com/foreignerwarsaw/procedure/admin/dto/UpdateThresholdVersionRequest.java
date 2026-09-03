package com.foreignerwarsaw.procedure.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateThresholdVersionRequest(
    BigDecimal value, String valueText, LocalDate effectiveFrom, String notes) {}
