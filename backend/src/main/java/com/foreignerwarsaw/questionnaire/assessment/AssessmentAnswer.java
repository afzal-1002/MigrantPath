package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.questionnaire.question.Question;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A typed answer to one stable {@link Question} within one {@link Assessment}
 * (docs/database/DATABASE.md §4, brief §24-§26). Exactly one of the scalar columns (or {@link
 * #selectedOptions} for MULTI_SELECT) is populated, matching the question's {@code questionType} -
 * {@code AssessmentAnswerService} is the single writer that enforces this; {@link #unsure} true
 * means none of them are (the "I don't know" sentinel, brief §12).
 *
 * <p>{@link #applicable} is recomputed by {@code QuestionVisibilityService} on every write to this
 * assessment (brief §28): once this answer's question is no longer visible under the current answer
 * set, it flips to {@code false}. The row itself is kept (re-showing the question restores the
 * prior value), but {@link com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts} and
 * completion validation only ever consider {@code applicable = true} rows.
 */
@Entity
@Table(name = "assessment_answers")
public class AssessmentAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private Assessment assessment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "string_value")
  private String stringValue;

  @Column(name = "boolean_value")
  private Boolean booleanValue;

  @Column(name = "integer_value")
  private Long integerValue;

  @Column(name = "decimal_value")
  private BigDecimal decimalValue;

  @Column(name = "date_value")
  private LocalDate dateValue;

  @Column(name = "reference_code", length = 50)
  private String referenceCode;

  @Column(name = "is_unsure", nullable = false)
  private boolean unsure;

  @Column(name = "is_applicable", nullable = false)
  private boolean applicable = true;

  @Column(name = "answered_at", nullable = false)
  private Instant answeredAt;

  @OneToMany(
      mappedBy = "assessmentAnswer",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<AssessmentAnswerOption> selectedOptions = new HashSet<>();

  protected AssessmentAnswer() {}

  public static AssessmentAnswer unanswered(Assessment assessment, Question question) {
    AssessmentAnswer answer = new AssessmentAnswer();
    answer.assessment = assessment;
    answer.question = question;
    return answer;
  }

  public UUID getId() {
    return id;
  }

  public Question getQuestion() {
    return question;
  }

  public boolean isUnsure() {
    return unsure;
  }

  public boolean isApplicable() {
    return applicable;
  }

  public void setApplicable(boolean applicable) {
    this.applicable = applicable;
  }

  public Instant getAnsweredAt() {
    return answeredAt;
  }

  public Set<AssessmentAnswerOption> getSelectedOptions() {
    return selectedOptions;
  }

  public void markUnsure(Instant at) {
    clearValues();
    this.unsure = true;
    this.answeredAt = at;
  }

  public void setBooleanValue(boolean value, Instant at) {
    clearValues();
    this.booleanValue = value;
    this.answeredAt = at;
  }

  public void setStringValue(String value, Instant at) {
    clearValues();
    this.stringValue = value;
    this.answeredAt = at;
  }

  public void setIntegerValue(long value, Instant at) {
    clearValues();
    this.integerValue = value;
    this.answeredAt = at;
  }

  public void setDecimalValue(BigDecimal value, Instant at) {
    clearValues();
    this.decimalValue = value;
    this.answeredAt = at;
  }

  public void setDateValue(LocalDate value, Instant at) {
    clearValues();
    this.dateValue = value;
    this.answeredAt = at;
  }

  public void setReferenceCode(String code, Instant at) {
    clearValues();
    this.referenceCode = code;
    this.answeredAt = at;
  }

  public void setSelectedOptionCodes(Set<String> codes, Instant at) {
    clearValues();
    this.selectedOptions.clear();
    for (String code : codes) {
      this.selectedOptions.add(new AssessmentAnswerOption(this, code));
    }
    this.answeredAt = at;
  }

  private void clearValues() {
    this.unsure = false;
    this.stringValue = null;
    this.booleanValue = null;
    this.integerValue = null;
    this.decimalValue = null;
    this.dateValue = null;
    this.referenceCode = null;
    this.selectedOptions.clear();
  }

  /**
   * The single typed value {@code ConditionEvaluator}/{@code AssessmentFacts} read - {@code null}
   * when {@link #unsure}, a {@link Set}{@code <String>} for a MULTI_SELECT answer, or the one
   * populated scalar column otherwise.
   */
  public Object logicalValue() {
    if (unsure) {
      return null;
    }
    if (!selectedOptions.isEmpty()) {
      return selectedOptions.stream()
          .map(AssessmentAnswerOption::getOptionCode)
          .collect(Collectors.toSet());
    }
    if (booleanValue != null) {
      return booleanValue;
    }
    if (integerValue != null) {
      return integerValue;
    }
    if (decimalValue != null) {
      return decimalValue;
    }
    if (dateValue != null) {
      return dateValue;
    }
    if (referenceCode != null) {
      return referenceCode;
    }
    return stringValue;
  }

  public String getStringValue() {
    return stringValue;
  }

  public Boolean getBooleanValue() {
    return booleanValue;
  }

  public Long getIntegerValue() {
    return integerValue;
  }

  public BigDecimal getDecimalValue() {
    return decimalValue;
  }

  public LocalDate getDateValue() {
    return dateValue;
  }

  public String getReferenceCode() {
    return referenceCode;
  }
}
