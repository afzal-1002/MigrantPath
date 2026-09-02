package com.foreignerwarsaw.questionnaire.visibility;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.common.web.ApiException;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** brief §68: publish-time cycle rejection. */
class DependencyGraphValidatorTest {

  private QuestionnaireQuestion question(String code) {
    QuestionnaireVersion version =
        QuestionnaireVersion.draft(Questionnaire.create("TEST", "Test"), 1, "Test", "Test", null);
    Question q = Question.create(code, code, QuestionType.BOOLEAN, SemanticDataType.GENERIC, null);
    ReflectionTestUtils.setField(q, "id", UUID.randomUUID());
    QuestionnaireQuestion qq =
        QuestionnaireQuestion.create(
            version,
            q,
            "SECTION",
            code,
            null,
            false,
            0,
            OptionSource.STATIC,
            false,
            VisibilityCombinator.ALL);
    ReflectionTestUtils.setField(qq, "id", UUID.randomUUID());
    return qq;
  }

  @Test
  void acyclicChain_passesValidation() {
    QuestionnaireQuestion a = question("A");
    QuestionnaireQuestion b = question("B");
    QuestionnaireQuestion c = question("C");
    List<QuestionDependency> deps =
        List.of(
            QuestionDependency.create(b, a, ComparisonOperator.EQUALS, "true"),
            QuestionDependency.create(c, b, ComparisonOperator.EQUALS, "true"));

    assertThatCode(() -> DependencyGraphValidator.requireAcyclic(deps)).doesNotThrowAnyException();
  }

  @Test
  void directCycle_isRejected() {
    QuestionnaireQuestion a = question("A");
    QuestionnaireQuestion b = question("B");
    List<QuestionDependency> deps =
        List.of(
            QuestionDependency.create(a, b, ComparisonOperator.EQUALS, "true"),
            QuestionDependency.create(b, a, ComparisonOperator.EQUALS, "true"));

    assertThatThrownBy(() -> DependencyGraphValidator.requireAcyclic(deps))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cycle");
  }

  @Test
  void transitiveCycle_isRejected() {
    QuestionnaireQuestion a = question("A");
    QuestionnaireQuestion b = question("B");
    QuestionnaireQuestion c = question("C");
    List<QuestionDependency> deps =
        List.of(
            QuestionDependency.create(a, b, ComparisonOperator.EQUALS, "true"),
            QuestionDependency.create(b, c, ComparisonOperator.EQUALS, "true"),
            QuestionDependency.create(c, a, ComparisonOperator.EQUALS, "true"));

    assertThatThrownBy(() -> DependencyGraphValidator.requireAcyclic(deps))
        .isInstanceOf(ApiException.class);
  }
}
