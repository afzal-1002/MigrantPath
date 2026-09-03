package com.foreignerwarsaw.procedure.source;

/**
 * How strongly one content item relies on a given {@link OfficialSource} (brief §26) - a legal
 * requirement may cite legislation (PRIMARY) plus a government explanatory page (SUPPORTING) plus a
 * local office page (OPERATIONAL), all at once. {@code LEGAL_BASIS} (Phase 6, brief §22) is
 * additionally valid for {@code rule_version_sources} only - the underlying statute/regulation a
 * Rule's condition tree implements, distinct from an explanatory PRIMARY page; the other four
 * `*_version_sources` tables' CHECK constraints (V31, unchanged) don't accept it.
 */
public enum SourceRole {
  PRIMARY,
  SUPPORTING,
  OPERATIONAL,
  LEGAL_BASIS
}
