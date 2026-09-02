package com.foreignerwarsaw.reference.country;

/**
 * Mirrors the CHECK constraint on {@code country_group_memberships.provenance_status} (V19).
 * Deliberately a two-value vocabulary, simpler than {@code OfficialSource.status}'s full
 * DRAFT/VERIFIED/NEEDS_REVIEW/OUTDATED/ARCHIVED lifecycle (DATABASE.md §3) - reference-data
 * provenance doesn't need a review workflow, only a visible flag so a consumer can tell whether a
 * row's dates were independently checked.
 */
public enum MembershipProvenanceStatus {
  /** The membership dates were checked against a specific, named source. */
  VERIFIED,
  /**
   * Compiled from general historical knowledge rather than a single authoritative source per date -
   * accurate about *whether* the membership exists, flagged as imprecise about exactly *when* it
   * started. See V19's migration comment and docs/reference/REFERENCE_DATA_SOURCES.md.
   */
  DRAFT
}
