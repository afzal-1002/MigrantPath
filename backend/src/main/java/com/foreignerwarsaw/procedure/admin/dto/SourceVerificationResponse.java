package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.SourceVerification;
import java.time.Instant;
import java.util.UUID;

public record SourceVerificationResponse(
    UUID id, Instant checkedAt, String checkedByEmail, String status, String notes) {

  public static SourceVerificationResponse from(SourceVerification v) {
    return new SourceVerificationResponse(
        v.getId(),
        v.getCheckedAt(),
        v.getCheckedBy() != null ? v.getCheckedBy().getEmail() : null,
        v.getStatus().name(),
        v.getNotes());
  }
}
