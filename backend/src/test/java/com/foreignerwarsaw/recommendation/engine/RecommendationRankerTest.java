package com.foreignerwarsaw.recommendation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.procedure.core.JurisdictionScope;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Deterministic ordering and the PRIMARY_MATCH/POSSIBLE_ALTERNATIVE demotion policy
 * (docs/recommendations/RANKING_POLICY.md, brief §22/§24/§44).
 */
class RecommendationRankerTest {

  private final RecommendationRanker ranker = new RecommendationRanker();

  private Procedure procedure(String code, Integer priority) {
    Procedure procedure =
        Procedure.create(code, null, "Test " + code, "desc", JurisdictionScope.NATIONAL);
    if (priority != null) {
      ReflectionTestUtils.setField(procedure, "recommendationPriority", priority);
    }
    return procedure;
  }

  private Candidate candidate(String code, Integer priority, RecommendationType type) {
    return new Candidate(procedure(code, priority), null, type, List.of());
  }

  @Test
  void categoriesAreOrderedPrimaryThenAlternativeThenMoreInfoThenNotApplicableThenUnavailable() {
    List<RankedCandidate> ranked =
        ranker.rank(
            List.of(
                candidate("Z_NOT_APPLICABLE", null, RecommendationType.NOT_APPLICABLE),
                candidate("A_PRIMARY", null, RecommendationType.PRIMARY_MATCH),
                candidate("M_MORE_INFO", null, RecommendationType.MORE_INFORMATION_REQUIRED),
                candidate("U_UNAVAILABLE", null, RecommendationType.UNAVAILABLE_FOR_ANALYSIS)));

    assertThat(ranked)
        .extracting(r -> r.procedure().getCode())
        .containsExactly("A_PRIMARY", "M_MORE_INFO", "Z_NOT_APPLICABLE", "U_UNAVAILABLE");
    assertThat(ranked).extracting(RankedCandidate::rank).containsExactly(1, 2, 3, 4);
  }

  @Test
  void withNoDeclaredPriority_everyMatchCandidateStaysPrimary_orderedAlphabetically() {
    List<RankedCandidate> ranked =
        ranker.rank(
            List.of(
                candidate("ZEBRA_PROCEDURE", null, RecommendationType.PRIMARY_MATCH),
                candidate("ALPHA_PROCEDURE", null, RecommendationType.PRIMARY_MATCH)));

    assertThat(ranked).allMatch(r -> r.type() == RecommendationType.PRIMARY_MATCH);
    assertThat(ranked)
        .extracting(r -> r.procedure().getCode())
        .containsExactly("ALPHA_PROCEDURE", "ZEBRA_PROCEDURE");
  }

  @Test
  void withADeclaredPriority_onlyTheBestPriorityCandidateStaysPrimary_othersDemoted() {
    List<RankedCandidate> ranked =
        ranker.rank(
            List.of(
                candidate("SECOND_CHOICE", 2, RecommendationType.PRIMARY_MATCH),
                candidate("TOP_CHOICE", 1, RecommendationType.PRIMARY_MATCH),
                candidate("NO_PRIORITY_SET", null, RecommendationType.PRIMARY_MATCH)));

    assertThat(ranked.get(0).procedure().getCode()).isEqualTo("TOP_CHOICE");
    assertThat(ranked.get(0).type()).isEqualTo(RecommendationType.PRIMARY_MATCH);
    assertThat(ranked.subList(1, 3))
        .allMatch(r -> r.type() == RecommendationType.POSSIBLE_ALTERNATIVE);
  }

  @Test
  void demotionNeverAppliesOutsideThePrimaryMatchCategory() {
    List<RankedCandidate> ranked =
        ranker.rank(
            List.of(
                candidate("HAS_PRIORITY", 1, RecommendationType.PRIMARY_MATCH),
                candidate("SOME_OTHER_MATCH", null, RecommendationType.MORE_INFORMATION_REQUIRED)));

    assertThat(ranked)
        .filteredOn(r -> r.procedure().getCode().equals("SOME_OTHER_MATCH"))
        .extracting(RankedCandidate::type)
        .containsExactly(RecommendationType.MORE_INFORMATION_REQUIRED);
  }

  @Test
  void tiesWithinTheSameCategoryAndPriorityBreakAlphabeticallyByProcedureCode() {
    List<RankedCandidate> ranked =
        ranker.rank(
            List.of(
                candidate("B_PROCEDURE", 1, RecommendationType.PRIMARY_MATCH),
                candidate("A_PROCEDURE", 1, RecommendationType.PRIMARY_MATCH)));

    assertThat(ranked)
        .extracting(r -> r.procedure().getCode())
        .containsExactly("A_PROCEDURE", "B_PROCEDURE");
  }
}
