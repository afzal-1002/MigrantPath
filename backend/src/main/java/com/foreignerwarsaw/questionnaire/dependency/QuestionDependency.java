package com.foreignerwarsaw.questionnaire.dependency;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * "Should this question be shown" branching (brief §13/§14) - NOT the immigration Rules Engine.
 * {@code questionnaireQuestion} is the gated question; {@code dependsOnQuestionnaireQuestion} is
 * the question whose current answer is compared, using the same {@link ComparisonOperator}
 * vocabulary Phase 6's {@code RuleCondition} will reuse (IMPLEMENTATION_PLAN.md 5.2). When a gated
 * question has more than one dependency row, {@link
 * QuestionnaireQuestion#getVisibilityCombinator()} decides whether all or any must hold (brief
 * §70).
 *
 * <p>{@code expectedValue} is raw JSON text (same {@code @JdbcTypeCode(SqlTypes.JSON)} convention
 * as {@link com.foreignerwarsaw.reference.authority.Office#getOpeningHours}) rather than a typed
 * Java value - a JSON scalar for every operator except {@code IN}/{@code NOT_IN}/{@code
 * CONTAINS}/{@code NOT_CONTAINS} (a JSON array); {@link
 * com.foreignerwarsaw.questionnaire.visibility.QuestionVisibilityService} parses it once per
 * evaluation via the shared {@code ObjectMapper}.
 */
@Entity
@Table(name = "question_dependencies")
public class QuestionDependency {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_question_id", nullable = false)
  private QuestionnaireQuestion questionnaireQuestion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "depends_on_questionnaire_question_id", nullable = false)
  private QuestionnaireQuestion dependsOnQuestionnaireQuestion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 25)
  private ComparisonOperator operator;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "expected_value", nullable = false, columnDefinition = "jsonb")
  private String expectedValue;

  protected QuestionDependency() {}

  public static QuestionDependency create(
      QuestionnaireQuestion questionnaireQuestion,
      QuestionnaireQuestion dependsOnQuestionnaireQuestion,
      ComparisonOperator operator,
      String expectedValueJson) {
    QuestionDependency dependency = new QuestionDependency();
    dependency.questionnaireQuestion = questionnaireQuestion;
    dependency.dependsOnQuestionnaireQuestion = dependsOnQuestionnaireQuestion;
    dependency.operator = operator;
    dependency.expectedValue = expectedValueJson;
    return dependency;
  }

  public UUID getId() {
    return id;
  }

  public QuestionnaireQuestion getQuestionnaireQuestion() {
    return questionnaireQuestion;
  }

  public QuestionnaireQuestion getDependsOnQuestionnaireQuestion() {
    return dependsOnQuestionnaireQuestion;
  }

  public ComparisonOperator getOperator() {
    return operator;
  }

  public String getExpectedValue() {
    return expectedValue;
  }
}
