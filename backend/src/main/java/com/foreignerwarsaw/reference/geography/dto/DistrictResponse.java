package com.foreignerwarsaw.reference.geography.dto;

import com.foreignerwarsaw.reference.geography.District;

public record DistrictResponse(String code, String name) {

  public static DistrictResponse from(District district) {
    return new DistrictResponse(district.getCode(), district.getCanonicalName());
  }
}
