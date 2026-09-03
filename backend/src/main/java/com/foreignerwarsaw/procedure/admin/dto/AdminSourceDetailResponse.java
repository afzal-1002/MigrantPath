package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.OfficialSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminSourceDetailResponse(
    UUID id,
    String title,
    String sourceUrl,
    String sourceType,
    String language,
    LocalDate publicationDate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String verificationStatus,
    Instant lastCheckedAt,
    Instant lastVerifiedAt,
    boolean active) {

  public static AdminSourceDetailResponse from(OfficialSource s) {
    return new AdminSourceDetailResponse(
        s.getId(),
        s.getTitle(),
        s.getSourceUrl(),
        s.getSourceType().name(),
        s.getLanguage(),
        s.getPublicationDate(),
        s.getEffectiveFrom(),
        s.getEffectiveTo(),
        s.getVerificationStatus().name(),
        s.getLastCheckedAt(),
        s.getLastVerifiedAt(),
        s.isActive());
  }
}
