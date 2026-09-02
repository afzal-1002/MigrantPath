package com.foreignerwarsaw.procedure.core;

/**
 * Mirrors {@code procedures.jurisdiction_scope}'s CHECK constraint (V22). {@code MIXED} is reserved
 * for a procedure whose *eligibility conditions themselves* differ by region
 * (docs/product/PROCEDURE_CATALOGUE.md's own jurisdiction-tag definition) - a national law
 * processed by a regional authority is still {@code NATIONAL}, not {@code MIXED} (none of the 8 MVP
 * procedures is actually {@code MIXED} - see V23's seed and the Phase 4 report's Conflicts
 * section).
 */
public enum JurisdictionScope {
  NATIONAL,
  REGIONAL,
  MUNICIPAL,
  MIXED
}
