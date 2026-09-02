package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.reference.authority.Office;

public record ProcedureOfficeRefResponse(
    String code,
    String name,
    String street,
    String buildingNumber,
    String postalCode,
    String cityCode,
    String phone) {

  public static ProcedureOfficeRefResponse from(Office office) {
    return new ProcedureOfficeRefResponse(
        office.getCode(),
        office.getCanonicalName(),
        office.getStreet(),
        office.getBuildingNumber(),
        office.getPostalCode(),
        office.getCity().getCode(),
        office.getPhone());
  }
}
