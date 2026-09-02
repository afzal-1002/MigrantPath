package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.core.Procedure;

/** List-endpoint item (brief §36) - deliberately minimal, no steps/documents/fees. */
public record ProcedureSummaryResponse(
    String code, String name, String category, String summary, String jurisdictionScope) {

  public static ProcedureSummaryResponse from(Procedure procedure) {
    return new ProcedureSummaryResponse(
        procedure.getCode(),
        procedure.getCanonicalName(),
        procedure.getCategory().getCode(),
        procedure.getShortDescription(),
        procedure.getJurisdictionScope().name());
  }
}
