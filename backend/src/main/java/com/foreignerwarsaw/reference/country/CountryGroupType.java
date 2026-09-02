package com.foreignerwarsaw.reference.country;

/** Mirrors the CHECK constraint on {@code country_groups.group_type} (V9) - see ADR-006. */
public enum CountryGroupType {
  /** A real EU-law/treaty category (EU membership, EEA, EFTA, Schengen). */
  LEGAL,
  /**
   * A useful aggregate this application defines for its own purposes, not itself a distinct legal
   * category (e.g. {@code EU_EEA_SWISS}).
   */
  CONVENIENCE
}
