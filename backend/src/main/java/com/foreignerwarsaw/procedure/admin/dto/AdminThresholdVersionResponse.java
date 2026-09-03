package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersionSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    Instant publishedAt,
    List<Source> sources) {

  public record Source(
      UUID officialSourceId,
      String title,
      String sourceUrl,
      String role,
      String verificationStatus) {
    static Source from(ThresholdVersionSource s) {
      return new Source(
          s.getOfficialSource().getId(),
          s.getOfficialSource().getTitle(),
          s.getOfficialSource().getSourceUrl(),
          s.getRole().name(),
          s.getOfficialSource().getVerificationStatus().name());
    }
  }

  public static AdminThresholdVersionResponse from(
      ThresholdVersion v, List<ThresholdVersionSource> sources) {
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
        v.getPublishedAt(),
        sources.stream().map(Source::from).toList());
  }
}
