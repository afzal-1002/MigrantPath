package com.foreignerwarsaw.questionnaire.question;

import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
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

/**
 * Per-{@link QuestionnaireVersion} presentation + gating configuration for one stable {@link
 * Question} (brief §6) - label, section, required-ness, option source, and how its {@code
 * QuestionDependency} rows combine. Kept separate from {@link Question} so the same semantic
 * question can be reworded, re-sectioned, or re-gated across versions without ever renaming the
 * stable {@code Question.code} that rule conditions and answers key off (brief §43).
 */
@Entity
@Table(name = "questionnaire_questions")
public class QuestionnaireQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_version_id", nullable = false)
  private QuestionnaireVersion questionnaireVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "section_code", nullable = false, length = 50)
  private String sectionCode;

  @Column(nullable = false, length = 500)
  private String label;

  @Column(name = "help_text", length = 1000)
  private String helpText;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "option_source", nullable = false, length = 20)
  private OptionSource optionSource = OptionSource.STATIC;

  @Column(name = "allow_unsure", nullable = false)
  private boolean allowUnsure;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_combinator", nullable = false, length = 5)
  private VisibilityCombinator visibilityCombinator = VisibilityCombinator.ALL;

  protected QuestionnaireQuestion() {}

  public static QuestionnaireQuestion create(
      QuestionnaireVersion questionnaireVersion,
      Question question,
      String sectionCode,
      String label,
      String helpText,
      boolean required,
      int sortOrder,
      OptionSource optionSource,
      boolean allowUnsure,
      VisibilityCombinator visibilityCombinator) {
    QuestionnaireQuestion questionnaireQuestion = new QuestionnaireQuestion();
    questionnaireQuestion.questionnaireVersion = questionnaireVersion;
    questionnaireQuestion.question = question;
    questionnaireQuestion.sectionCode = sectionCode;
    questionnaireQuestion.label = label;
    questionnaireQuestion.helpText = helpText;
    questionnaireQuestion.required = required;
    questionnaireQuestion.sortOrder = sortOrder;
    questionnaireQuestion.optionSource = optionSource;
    questionnaireQuestion.allowUnsure = allowUnsure;
    questionnaireQuestion.visibilityCombinator = visibilityCombinator;
    return questionnaireQuestion;
  }

  public UUID getId() {
    return id;
  }

  public QuestionnaireVersion getQuestionnaireVersion() {
    return questionnaireVersion;
  }

  public Question getQuestion() {
    return question;
  }

  public String getSectionCode() {
    return sectionCode;
  }

  public String getLabel() {
    return label;
  }

  public String getHelpText() {
    return helpText;
  }

  public boolean isRequired() {
    return required;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public OptionSource getOptionSource() {
    return optionSource;
  }

  public boolean isAllowUnsure() {
    return allowUnsure;
  }

  public VisibilityCombinator getVisibilityCombinator() {
    return visibilityCombinator;
  }
}
