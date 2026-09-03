package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.threshold.Threshold;

public record AdminThresholdSummaryResponse(
    String code, String canonicalName, String valueType, String unit, boolean active) {

  public static AdminThresholdSummaryResponse from(Threshold t) {
    return new AdminThresholdSummaryResponse(
        t.getCode(), t.getCanonicalName(), t.getValueType().name(), t.getUnit(), t.isActive());
  }
}
