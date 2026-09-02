package com.foreignerwarsaw.questionnaire.core;

import java.util.Map;

/**
 * Display titles for the fixed, small section taxonomy the seeded MVP wizard uses (brief §16) -
 * shared by {@link QuestionnaireQueryService} (full structure) and {@code AssessmentQueryService}
 * (per-assessment visible-question view) so the two never drift. Not a database table: these are UI
 * grouping labels, not legal/administrative content, and the section codes are a closed set with no
 * admin-editing need in Phase 5 (brief §40).
 */
public final class SectionTitles {

  private static final Map<String, String> TITLES =
      Map.ofEntries(
          Map.entry("ABOUT_YOU", "About you"),
          Map.entry("CURRENT_STATUS", "Your current status"),
          Map.entry("YOUR_GOAL", "What do you want to do?"),
          Map.entry("WORK", "Work"),
          Map.entry("STUDY", "Study"),
          Map.entry("FAMILY", "Family"),
          Map.entry("LONG_TERM", "Time in Poland"));

  private SectionTitles() {}

  public static String titleFor(String sectionCode) {
    return TITLES.getOrDefault(sectionCode, sectionCode);
  }
}
