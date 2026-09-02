package com.foreignerwarsaw.reference.country;

/**
 * Mirrors the CHECK constraint on {@code countries.code_standard} (V18). Distinguishes an
 * officially assigned ISO 3166-1 alpha-2 code from a code this application supports anyway despite
 * it never having been assigned by the ISO 3166 Maintenance Agency - see {@link
 * Country#isOfficiallyAssigned()} and docs/reference/REFERENCE_DATA_SOURCES.md for why {@code XK}
 * (Kosovo) is the one row currently in the latter category.
 */
public enum CountryCodeStandard {
  /** Currently one of the 249 officially assigned ISO 3166-1 alpha-2 codes. */
  ISO_3166_1,
  /**
   * Not an ISO 3166-1 code at all - a user-assigned/exceptionally-reserved code this application
   * chooses to support because it's operationally useful (e.g. as a country of citizenship), even
   * though the ISO 3166 Maintenance Agency never assigned it.
   */
  USER_ASSIGNED
}
