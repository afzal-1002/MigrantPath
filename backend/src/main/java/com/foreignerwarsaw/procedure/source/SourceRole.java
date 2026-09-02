package com.foreignerwarsaw.procedure.source;

/**
 * How strongly one content item relies on a given {@link OfficialSource} (brief §26) - a legal
 * requirement may cite legislation (PRIMARY) plus a government explanatory page (SUPPORTING) plus a
 * local office page (OPERATIONAL), all at once.
 */
public enum SourceRole {
  PRIMARY,
  SUPPORTING,
  OPERATIONAL
}
