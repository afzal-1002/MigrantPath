package com.foreignerwarsaw.questionnaire.assessment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link AssessmentFacts} snapshot (brief §37) - the one place that decides which
 * answers Phase 6 will eventually see.
 */
@Service
public class AssessmentFactsService {

  private final AssessmentAnswerRepository assessmentAnswerRepository;
  private final Clock clock;

  public AssessmentFactsService(
      AssessmentAnswerRepository assessmentAnswerRepository, Clock clock) {
    this.assessmentAnswerRepository = assessmentAnswerRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AssessmentFacts buildFacts(Assessment assessment) {
    List<AssessmentAnswer> answers =
        assessmentAnswerRepository.findByAssessment_Id(assessment.getId());
    Map<String, Object> answersByCode = new HashMap<>();
    for (AssessmentAnswer answer : answers) {
      if (!answer.isApplicable() || answer.isUnsure()) {
        continue;
      }
      answersByCode.put(answer.getQuestion().getCode(), answer.logicalValue());
    }

    LocalDate evaluationDate =
        assessment.getCompletedAt() != null
            ? assessment.getCompletedAt().atZone(clock.getZone()).toLocalDate()
            : LocalDate.now(clock);

    return new AssessmentFacts(
        assessment.getId(),
        assessment.getUser().getId(),
        assessment.getQuestionnaireVersion().getId(),
        assessment.getQuestionnaire().getCode(),
        assessment.getQuestionnaireVersion().getVersionNumber(),
        assessment.getStatus(),
        assessment.getCompletedAt(),
        evaluationDate,
        Map.copyOf(answersByCode));
  }
}
