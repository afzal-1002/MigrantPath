package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.fee.FeeVersion;
import java.math.BigDecimal;

public record FeeResponse(
    String code,
    String feeType,
    BigDecimal amount,
    String currency,
    String description,
    String paymentInstructions,
    Boolean refundable) {

  public static FeeResponse from(FeeVersion fee) {
    return new FeeResponse(
        fee.getFee().getStableCode(),
        fee.getFee().getFeeType().name(),
        fee.getAmount(),
        fee.getCurrency(),
        fee.getDescription(),
        fee.getPaymentInstructions(),
        fee.getRefundable());
  }
}
