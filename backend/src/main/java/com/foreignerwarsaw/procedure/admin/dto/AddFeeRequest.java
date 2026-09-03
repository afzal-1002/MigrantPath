package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.fee.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AddFeeRequest(
    @NotBlank String stableCode,
    @NotNull FeeType feeType,
    @NotNull BigDecimal amount,
    @NotBlank String currency) {}
