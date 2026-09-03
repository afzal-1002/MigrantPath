package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.time.LocalDate;

/** One row of the admin procedure list (brief §17). */
public record AdminProcedureSummaryResponse(
    String code,
    String categoryCode,
    String canonicalName,
    String jurisdictionScope,
    boolean active,
    Integer activeVersionNumber,
    LocalDate activeVersionEffectiveFrom,
    Integer latestVersionNumber,
    String latestVersionStatus) {

  public static AdminProcedureSummaryResponse from(
      Procedure procedure, ProcedureVersion active, ProcedureVersion latest) {
    return new AdminProcedureSummaryResponse(
        procedure.getCode(),
        procedure.getCategory().getCode(),
        procedure.getCanonicalName(),
        procedure.getJurisdictionScope().name(),
        procedure.isActive(),
        active != null ? active.getVersionNumber() : null,
        active != null ? active.getEffectiveFrom() : null,
        latest != null ? latest.getVersionNumber() : null,
        latest != null ? latest.getStatus().name() : null);
  }
}
