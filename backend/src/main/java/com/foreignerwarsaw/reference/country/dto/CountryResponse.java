package com.foreignerwarsaw.reference.country.dto;

import com.foreignerwarsaw.reference.country.Country;

/**
 * Deliberately minimal (brief §23) - a registration/onboarding dropdown needs a code and a name,
 * nothing else.
 */
public record CountryResponse(String code, String name) {

  public static CountryResponse from(Country country) {
    return new CountryResponse(country.getCode(), country.getCanonicalName());
  }
}
