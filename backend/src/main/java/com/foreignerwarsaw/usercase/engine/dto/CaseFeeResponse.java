package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.core.UserCaseFee;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CaseFeeResponse(
    UUID id,
    String stableCode,
    String feeType,
    BigDecimal amount,
    String currency,
    String description,
    String paymentInstructions,
    int sortOrder,
    String status,
    Instant paidAt) {

  public static CaseFeeResponse from(UserCaseFee fee) {
    return new CaseFeeResponse(
        fee.getId(),
        fee.getStableCode(),
        fee.getFeeType().name(),
        fee.getAmountSnapshot(),
        fee.getCurrencySnapshot(),
        fee.getDescriptionSnapshot(),
        fee.getPaymentInstructionsSnapshot(),
        fee.getSortOrder(),
        fee.getStatus().name(),
        fee.getPaidAt());
  }
}
