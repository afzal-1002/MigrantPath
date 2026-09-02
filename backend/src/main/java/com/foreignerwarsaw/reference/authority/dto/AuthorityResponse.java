package com.foreignerwarsaw.reference.authority.dto;

import com.foreignerwarsaw.reference.authority.Authority;

public record AuthorityResponse(
    String code,
    String name,
    String authorityType,
    String jurisdictionCode,
    String officialWebsite) {

  public static AuthorityResponse from(Authority authority) {
    return new AuthorityResponse(
        authority.getCode(),
        authority.getCanonicalName(),
        authority.getAuthorityType(),
        authority.getJurisdiction().getCode(),
        authority.getOfficialWebsite());
  }
}
