package com.foreignerwarsaw.reference.geography.dto;

import com.foreignerwarsaw.reference.geography.Region;

public record RegionResponse(String code, String name, String regionType) {

  public static RegionResponse from(Region region) {
    return new RegionResponse(region.getCode(), region.getCanonicalName(), region.getRegionType());
  }
}
