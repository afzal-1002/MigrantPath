package com.foreignerwarsaw.procedure.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateFeeRequest(
    @NotNull BigDecimal amount,
    @NotBlank String currency,
    String description,
    String paymentInstructions,
    Boolean refundable) {}
