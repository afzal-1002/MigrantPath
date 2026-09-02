package com.foreignerwarsaw.questionnaire.assessment;

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
 * One selected option code for a MULTI_SELECT {@link AssessmentAnswer} (brief §26) - a join table,
 * not a JSONB array, for queryability.
 */
@Entity
@Table(name = "assessment_answer_options")
public class AssessmentAnswerOption {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_answer_id", nullable = false)
  private AssessmentAnswer assessmentAnswer;

  @Column(name = "option_code", nullable = false, length = 50)
  private String optionCode;

  protected AssessmentAnswerOption() {}

  public AssessmentAnswerOption(AssessmentAnswer assessmentAnswer, String optionCode) {
    this.assessmentAnswer = assessmentAnswer;
    this.optionCode = optionCode;
  }

  public UUID getId() {
    return id;
  }

  public String getOptionCode() {
    return optionCode;
  }
}
