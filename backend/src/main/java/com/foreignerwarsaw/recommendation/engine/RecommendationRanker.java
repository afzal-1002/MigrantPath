package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.recommendation.core.RecommendationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic ordering across every candidate in one run (brief §22/§44 - full policy in
 * docs/recommendations/RANKING_POLICY.md). Never AI, never a percentage - two inputs only: category
 * precedence and, within the match category, {@link
 * com.foreignerwarsaw.procedure.core.Procedure#getRecommendationPriority()} (reviewed content only,
 * brief §24 - unset for every real Procedure today, see the field's own Javadoc). The final
 * tie-breaker is always the Procedure's stable {@code code}, alphabetically (brief §44) - never
 * database/collection retrieval order.
 */
@Component
public class RecommendationRanker {

  private static final Map<RecommendationType, Integer> CATEGORY_ORDER =
      Map.of(
          RecommendationType.PRIMARY_MATCH, 0,
          RecommendationType.POSSIBLE_ALTERNATIVE, 1,
          RecommendationType.MORE_INFORMATION_REQUIRED, 2,
          RecommendationType.NOT_APPLICABLE, 3,
          RecommendationType.UNAVAILABLE_FOR_ANALYSIS, 4);

  public List<RankedCandidate> rank(List<Candidate> candidates) {
    List<Candidate> demoted = demoteSecondaryMatches(candidates);

    List<Candidate> ordered =
        demoted.stream()
            .sorted(
                Comparator.<Candidate>comparingInt(c -> CATEGORY_ORDER.get(c.type()))
                    .thenComparing(
                        this::priorityForSort, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(c -> c.procedure().getCode()))
            .toList();

    List<RankedCandidate> ranked = new ArrayList<>();
    int rank = 1;
    for (Candidate candidate : ordered) {
      ranked.add(
          new RankedCandidate(
              candidate.procedure(),
              candidate.procedureVersion(),
              candidate.type(),
              candidate.ruleResults(),
              rank++));
    }
    return ranked;
  }

  /**
   * Among candidates the classifier put in {@code PRIMARY_MATCH}: if none of them has an explicit
   * {@code recommendationPriority}, every one of them stays {@code PRIMARY_MATCH} (brief §43 -
   * multiple primary matches are legitimate, and there is no reviewed signal to rank them by). If
   * at least one does, only the candidate(s) sharing the single best (lowest) priority value stay
   * {@code PRIMARY_MATCH}; every other match-candidate - including one with no priority set at all
   * - is demoted to {@code POSSIBLE_ALTERNATIVE}.
   */
  private List<Candidate> demoteSecondaryMatches(List<Candidate> candidates) {
    List<Candidate> matches =
        candidates.stream().filter(c -> c.type() == RecommendationType.PRIMARY_MATCH).toList();
    boolean anyPriorityDeclared =
        matches.stream().anyMatch(c -> c.procedure().getRecommendationPriority() != null);
    if (!anyPriorityDeclared) {
      return candidates;
    }

    int bestPriority =
        matches.stream()
            .map(c -> c.procedure().getRecommendationPriority())
            .filter(p -> p != null)
            .mapToInt(Integer::intValue)
            .min()
            .orElseThrow();

    return candidates.stream()
        .map(
            c -> {
              if (c.type() != RecommendationType.PRIMARY_MATCH) {
                return c;
              }
              Integer priority = c.procedure().getRecommendationPriority();
              boolean isTopTier = priority != null && priority == bestPriority;
              return isTopTier
                  ? c
                  : new Candidate(
                      c.procedure(),
                      c.procedureVersion(),
                      RecommendationType.POSSIBLE_ALTERNATIVE,
                      c.ruleResults());
            })
        .collect(Collectors.toList());
  }

  private Integer priorityForSort(Candidate candidate) {
    return candidate.procedure().getRecommendationPriority();
  }
}
