package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.authority.ProcedureAuthority;

public record ProcedureAuthorityRefResponse(
    String code, String name, String role, String officialWebsite) {

  public static ProcedureAuthorityRefResponse from(ProcedureAuthority procedureAuthority) {
    var authority = procedureAuthority.getAuthority();
    return new ProcedureAuthorityRefResponse(
        authority.getCode(),
        authority.getCanonicalName(),
        procedureAuthority.getRole().name(),
        authority.getOfficialWebsite());
  }
}
