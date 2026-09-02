package com.foreignerwarsaw.questionnaire.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.questionnaire.core.Questionnaire;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.dependency.QuestionDependency;
import com.foreignerwarsaw.questionnaire.question.OptionSource;
import com.foreignerwarsaw.questionnaire.question.Question;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.questionnaire.question.SemanticDataType;
import com.foreignerwarsaw.questionnaire.question.VisibilityCombinator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Direct unit tests of the deterministic visibility engine (brief §67/§79) - no database, plain
 * in-memory entity graphs with hand-assigned ids ({@link ReflectionTestUtils}, the same pattern
 * {@code ProcedurePublishingServiceTest} uses for transient JPA entities).
 */
class QuestionVisibilityServiceTest {

  private final QuestionVisibilityService service =
      new QuestionVisibilityService(new ObjectMapper());
  private final QuestionnaireVersion version =
      QuestionnaireVersion.draft(Questionnaire.create("TEST", "Test"), 1, "Test", "Test", null);

  private QuestionnaireQuestion question(String code, VisibilityCombinator combinator) {
    Question q = Question.create(code, code, QuestionType.BOOLEAN, SemanticDataType.GENERIC, null);
    ReflectionTestUtils.setField(q, "id", UUID.randomUUID());
    QuestionnaireQuestion qq =
        QuestionnaireQuestion.create(
            version, q, "SECTION", code, null, false, 0, OptionSource.STATIC, false, combinator);
    ReflectionTestUtils.setField(qq, "id", UUID.randomUUID());
    return qq;
  }

  private QuestionDependency dependsOn(
      QuestionnaireQuestion gated, QuestionnaireQuestion source, String expectedValueJson) {
    return QuestionDependency.create(gated, source, ComparisonOperator.EQUALS, expectedValueJson);
  }

  @Test
  void questionWithNoDependencies_isAlwaysVisible() {
    QuestionnaireQuestion a = question("A", VisibilityCombinator.ALL);
    Set<UUID> visible =
        service.computeVisibleQuestionnaireQuestionIds(List.of(a), List.of(), Map.of());
    assertThat(visible).containsExactly(a.getId());
  }

  @Test
  void allCombinator_requiresEveryConditionToHold() {
    QuestionnaireQuestion purpose = question("PURPOSE", VisibilityCombinator.ALL);
    QuestionnaireQuestion hasOffer = question("HAS_OFFER", VisibilityCombinator.ALL);
    QuestionnaireQuestion salary = question("SALARY", VisibilityCombinator.ALL);
    List<QuestionDependency> deps =
        List.of(dependsOn(salary, purpose, "true"), dependsOn(salary, hasOffer, "true"));

    // Only one of the two conditions holds -> not visible.
    Map<UUID, Object> answers = Map.of(purpose.getQuestion().getId(), true);
    Set<UUID> visible =
        service.computeVisibleQuestionnaireQuestionIds(
            List.of(purpose, hasOffer, salary), deps, answers);
    assertThat(visible).doesNotContain(salary.getId());

    // Both hold -> visible.
    Map<UUID, Object> bothAnswers =
        Map.of(purpose.getQuestion().getId(), true, hasOffer.getQuestion().getId(), true);
    Set<UUID> visibleBoth =
        service.computeVisibleQuestionnaireQuestionIds(
            List.of(purpose, hasOffer, salary), deps, bothAnswers);
    assertThat(visibleBoth).contains(salary.getId());
  }

  @Test
  void anyCombinator_requiresAtLeastOneConditionToHold() {
    QuestionnaireQuestion work = question("WORK", VisibilityCombinator.ALL);
    QuestionnaireQuestion highlyQualified = question("HQ", VisibilityCombinator.ALL);
    QuestionnaireQuestion hasOffer = question("HAS_OFFER", VisibilityCombinator.ANY);
    List<QuestionDependency> deps =
        List.of(dependsOn(hasOffer, work, "true"), dependsOn(hasOffer, highlyQualified, "true"));

    Map<UUID, Object> answers = Map.of(highlyQualified.getQuestion().getId(), true);
    Set<UUID> visible =
        service.computeVisibleQuestionnaireQuestionIds(
            List.of(work, highlyQualified, hasOffer), deps, answers);
    assertThat(visible).contains(hasOffer.getId());
  }

  @Test
  void hiddenPrerequisite_cascadesHiddenToDependentQuestion() {
    // C depends on B, B depends on A. A's condition fails, so B is hidden - and C, whose
    // condition references B's answer, must never see a stale/inapplicable answer for B (brief
    // §28) - it should evaluate as if B had never been answered.
    QuestionnaireQuestion a = question("A", VisibilityCombinator.ALL);
    QuestionnaireQuestion b = question("B", VisibilityCombinator.ALL);
    QuestionnaireQuestion c = question("C", VisibilityCombinator.ALL);
    List<QuestionDependency> deps = List.of(dependsOn(b, a, "true"), dependsOn(c, b, "true"));

    // A is false (so B is hidden), but B nonetheless has a stale "true" answer on record.
    Map<UUID, Object> answers =
        Map.of(a.getQuestion().getId(), false, b.getQuestion().getId(), true);
    Set<UUID> visible =
        service.computeVisibleQuestionnaireQuestionIds(List.of(a, b, c), deps, answers);

    assertThat(visible).contains(a.getId());
    assertThat(visible).doesNotContain(b.getId());
    assertThat(visible).doesNotContain(c.getId());
  }

  @Test
  void cyclicDependency_throwsRatherThanLoopingForever() {
    QuestionnaireQuestion a = question("A", VisibilityCombinator.ALL);
    QuestionnaireQuestion b = question("B", VisibilityCombinator.ALL);
    List<QuestionDependency> deps = List.of(dependsOn(a, b, "true"), dependsOn(b, a, "true"));

    assertThatThrownBy(
            () -> service.computeVisibleQuestionnaireQuestionIds(List.of(a, b), deps, Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cyclic");
  }
}
