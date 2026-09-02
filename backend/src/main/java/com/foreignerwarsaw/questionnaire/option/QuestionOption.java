package com.foreignerwarsaw.questionnaire.option;

import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A selectable option for a {@code STATIC}-{@code optionSource} {@link QuestionnaireQuestion}
 * (brief §9/§11) - e.g. one row of {@code CURRENT_LEGAL_STATUS}'s option list. Never used for a
 * reference-backed question (Country/Region/City/District) - those resolve their options from Phase
 * 3 reference data instead (brief §11).
 */
@Entity
@Table(name = "question_options")
public class QuestionOption {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_question_id", nullable = false)
  private QuestionnaireQuestion questionnaireQuestion;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 300)
  private String label;

  @Column(length = 500)
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "reference_value", length = 50)
  private String referenceValue;

  protected QuestionOption() {}

  public static QuestionOption create(
      QuestionnaireQuestion questionnaireQuestion,
      String code,
      String label,
      String description,
      int sortOrder) {
    QuestionOption option = new QuestionOption();
    option.questionnaireQuestion = questionnaireQuestion;
    option.code = code;
    option.label = label;
    option.description = description;
    option.sortOrder = sortOrder;
    return option;
  }

  public UUID getId() {
    return id;
  }

  public QuestionnaireQuestion getQuestionnaireQuestion() {
    return questionnaireQuestion;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public String getReferenceValue() {
    return referenceValue;
  }
}
