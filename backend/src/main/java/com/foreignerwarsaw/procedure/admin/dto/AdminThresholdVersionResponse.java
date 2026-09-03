package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminThresholdVersionResponse(
    UUID id,
    String thresholdCode,
    String status,
    BigDecimal value,
    String valueText,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String notes,
    long lockVersion,
    String createdByEmail,
    String submittedByEmail,
    String approvedByEmail,
    String publishedByEmail,
    Instant publishedAt) {

  public static AdminThresholdVersionResponse from(ThresholdVersion v) {
    return new AdminThresholdVersionResponse(
        v.getId(),
        v.getThreshold().getCode(),
        v.getStatus().name(),
        v.getValue(),
        v.getValueText(),
        v.getEffectiveFrom(),
        v.getEffectiveTo(),
        v.getNotes(),
        v.getLockVersion(),
        v.getCreatedBy() != null ? v.getCreatedBy().getEmail() : null,
        v.getSubmittedBy() != null ? v.getSubmittedBy().getEmail() : null,
        v.getApprovedBy() != null ? v.getApprovedBy().getEmail() : null,
        v.getPublishedBy() != null ? v.getPublishedBy().getEmail() : null,
        v.getPublishedAt());
  }
}
