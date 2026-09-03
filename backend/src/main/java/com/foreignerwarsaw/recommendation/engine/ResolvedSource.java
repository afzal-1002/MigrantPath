package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;

/**
 * One deduplicated {@link OfficialSource} backing a recommendation, with the most authoritative
 * {@link SourceRole} it was found under (brief §30/§56) - the same source may back a Procedure's
 * content, a Rule's legal basis, and a Threshold's value at once; only the single best role is kept
 * for display.
 */
public record ResolvedSource(OfficialSource source, SourceRole role) {}
