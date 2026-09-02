package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.time.LocalDate;

/**
 * Internal admin-facing view - unlike the public {@code ProcedureDetailResponse}, this may show any
 * status including DRAFT (the internal API's own role checks are what keep this from being publicly
 * reachable, not the DTO shape).
 */
public record ProcedureVersionAdminResponse(
    String procedureCode,
    int versionNumber,
    String status,
    String title,
    LocalDate effectiveFrom,
    LocalDate effectiveTo) {

  public static ProcedureVersionAdminResponse from(ProcedureVersion version) {
    return new ProcedureVersionAdminResponse(
        version.getProcedure().getCode(),
        version.getVersionNumber(),
        version.getStatus().name(),
        version.getTitle(),
        version.getEffectiveFrom(),
        version.getEffectiveTo());
  }
}
