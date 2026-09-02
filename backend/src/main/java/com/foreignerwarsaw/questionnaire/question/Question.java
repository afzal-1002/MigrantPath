package com.foreignerwarsaw.questionnaire.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Stable, rule-facing question identity (docs/database/DATABASE.md §4, brief §5/§43) - {@code code}
 * and {@code fieldKey} are domain contracts a future Phase 6 {@code RuleCondition} and every {@code
 * AssessmentAnswer} reference by, and must never be renamed once in use. Never coupled to any
 * Angular type or English UI text (brief §5) - display copy lives on {@link
 * com.foreignerwarsaw.questionnaire.core.QuestionnaireQuestion} instead, so the same semantic
 * question could in principle be reworded across questionnaire versions without ever touching this
 * row.
 */
@Entity
@Table(name = "questions")
public class Question {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 80)
  private String code;

  /** The camelCase name a {@code RuleCondition.field}/{@code AssessmentFacts} map key uses. */
  @Column(name = "field_key", nullable = false, length = 80)
  private String fieldKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, length = 20)
  private QuestionType questionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "semantic_data_type", length = 20)
  private SemanticDataType semanticDataType;

  @Column(length = 30)
  private String unit;

  @Column(nullable = false)
  private boolean active = true;

  protected Question() {}

  public static Question create(
      String code,
      String fieldKey,
      QuestionType questionType,
      SemanticDataType semanticDataType,
      String unit) {
    Question question = new Question();
    question.code = code;
    question.fieldKey = fieldKey;
    question.questionType = questionType;
    question.semanticDataType = semanticDataType;
    question.unit = unit;
    return question;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public QuestionType getQuestionType() {
    return questionType;
  }

  public SemanticDataType getSemanticDataType() {
    return semanticDataType;
  }

  public String getUnit() {
    return unit;
  }

  public boolean isActive() {
    return active;
  }
}
