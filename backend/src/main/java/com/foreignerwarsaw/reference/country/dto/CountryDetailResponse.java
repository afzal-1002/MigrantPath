package com.foreignerwarsaw.reference.country.dto;

import com.foreignerwarsaw.reference.country.Country;
import java.util.List;

/**
 * {@code groups} are the codes active as of the request's evaluation date (default: today) -
 * overlapping entries are expected and correct (e.g. Germany is both {@code EEA} and {@code
 * SCHENGEN}), not a bug (brief §24).
 *
 * <p>{@code codeStandard}/{@code officiallyAssigned} (V18) let a consumer tell whether {@code code}
 * is an actual ISO 3166-1 assignment - {@code false} only for {@code XK} (Kosovo) as of Phase 3.
 * Deliberately surfaced here (not just in the database) so this fact is honestly visible anywhere
 * the API is consumed, not just to someone reading the schema directly.
 */
public record CountryDetailResponse(
    String code,
    String name,
    List<String> groups,
    String codeStandard,
    boolean officiallyAssigned) {

  public static CountryDetailResponse of(Country country, List<String> groups) {
    return new CountryDetailResponse(
        country.getCode(),
        country.getCanonicalName(),
        groups,
        country.getCodeStandard().name(),
        country.isOfficiallyAssigned());
  }
}
