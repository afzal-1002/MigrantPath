package com.foreignerwarsaw.reference.geography.dto;

import com.foreignerwarsaw.reference.geography.City;

public record CityResponse(String code, String name) {

  public static CityResponse from(City city) {
    return new CityResponse(city.getCode(), city.getCanonicalName());
  }
}
