package com.foreignerwarsaw.procedure.source;

/**
 * Fixed, closed vocabulary (brief §23) - deliberately excludes BLOG/REDDIT/LAW_FIRM. Those may help
 * identify what to research (CLAUDE.md's own sourcing priority order already says as much for
 * procedure content generally) but are never themselves an {@link OfficialSource} backing a
 * published legal requirement.
 */
public enum SourceType {
  LEGISLATION,
  GOVERNMENT_GUIDANCE,
  OFFICIAL_SERVICE_PAGE,
  OFFICIAL_FORM,
  OFFICIAL_FEE_SCHEDULE,
  OFFICIAL_NOTICE,
  OTHER_OFFICIAL
}
