package com.foreignerwarsaw.questionnaire.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.TestcontainersConfiguration;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.questionnaire.core.Questionnaire;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireRepository;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersionRepository;
import com.foreignerwarsaw.questionnaire.question.Question;
import com.foreignerwarsaw.questionnaire.question.QuestionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import com.foreignerwarsaw.questionnaire.question.SemanticDataType;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Real Testcontainers PostgreSQL - the "one IN_PROGRESS assessment per user per questionnaire"
 * constraint (brief §34/§63) and scalar/multi-select answer persistence (brief §80).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AssessmentRepositoryTest {

  @Autowired private UserRepository userRepository;
  @Autowired private QuestionnaireRepository questionnaireRepository;
  @Autowired private QuestionnaireVersionRepository questionnaireVersionRepository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private AssessmentAnswerRepository assessmentAnswerRepository;
  @Autowired private TestEntityManager testEntityManager;

  private User newUser() {
    return userRepository.saveAndFlush(
        User.newRegistration(
            "assessment-repo-" + UUID.randomUUID() + "@example.com", "hash", "Pat"));
  }

  private QuestionnaireVersion newPublishedVersion() {
    Questionnaire questionnaire =
        questionnaireRepository.saveAndFlush(
            Questionnaire.create("TEST_QNR_" + UUID.randomUUID(), "Test"));
    QuestionnaireVersion version = QuestionnaireVersion.draft(questionnaire, 1, "T", "D", null);
    ReflectionTestUtils.setField(version, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(version, "effectiveFrom", java.time.LocalDate.now().minusDays(1));
    return questionnaireVersionRepository.saveAndFlush(version);
  }

  private Question newQuestion(String code) {
    return questionRepository.saveAndFlush(
        Question.create(code, code, QuestionType.BOOLEAN, SemanticDataType.GENERIC, null));
  }

  @Test
  void onlyOneInProgressAssessmentPerUserPerQuestionnaire_isEnforced() {
    User user = newUser();
    QuestionnaireVersion version = newPublishedVersion();

    assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now()));

    assertThatThrownBy(
            () -> assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aCompletedAssessmentDoesNotBlockStartingANewOne() {
    User user = newUser();
    QuestionnaireVersion version = newPublishedVersion();

    Assessment first =
        assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now()));
    first.complete(Instant.now());
    assessmentRepository.saveAndFlush(first);

    Assessment second =
        assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now()));
    assertThat(second.getId()).isNotEqualTo(first.getId());
  }

  @Test
  void scalarAndMultiSelectAnswers_persistWithTypedColumns() {
    User user = newUser();
    QuestionnaireVersion version = newPublishedVersion();
    Assessment assessment =
        assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now()));

    Question booleanQuestion = newQuestion("TEST_BOOLEAN_" + UUID.randomUUID());
    AssessmentAnswer booleanAnswer = AssessmentAnswer.unanswered(assessment, booleanQuestion);
    booleanAnswer.setBooleanValue(true, Instant.now());
    assessmentAnswerRepository.saveAndFlush(booleanAnswer);

    Question multiSelectQuestion = newQuestion("TEST_MULTI_" + UUID.randomUUID());
    AssessmentAnswer multiAnswer = AssessmentAnswer.unanswered(assessment, multiSelectQuestion);
    multiAnswer.setSelectedOptionCodes(java.util.Set.of("WORK", "STUDY"), Instant.now());
    assessmentAnswerRepository.saveAndFlush(multiAnswer);
    testEntityManager.clear();

    var reloadedMulti =
        assessmentAnswerRepository
            .findByAssessment_IdAndQuestion_Id(assessment.getId(), multiSelectQuestion.getId())
            .orElseThrow();
    assertThat(reloadedMulti.getSelectedOptions()).hasSize(2);
  }

  @Test
  void oneAnswerPerAssessmentPerQuestion_isEnforced() {
    User user = newUser();
    QuestionnaireVersion version = newPublishedVersion();
    Assessment assessment =
        assessmentRepository.saveAndFlush(Assessment.start(user, version, Instant.now()));
    Question question = newQuestion("TEST_DUP_ANSWER_" + UUID.randomUUID());

    AssessmentAnswer first = AssessmentAnswer.unanswered(assessment, question);
    first.setBooleanValue(true, Instant.now());
    assessmentAnswerRepository.saveAndFlush(first);

    assertThatThrownBy(
            () -> {
              AssessmentAnswer duplicate = AssessmentAnswer.unanswered(assessment, question);
              duplicate.setBooleanValue(false, Instant.now());
              assessmentAnswerRepository.saveAndFlush(duplicate);
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
