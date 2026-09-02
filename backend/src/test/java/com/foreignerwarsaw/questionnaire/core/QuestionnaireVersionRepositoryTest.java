package com.foreignerwarsaw.questionnaire.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.TestcontainersConfiguration;
import com.foreignerwarsaw.procedure.PublicationStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Real Testcontainers PostgreSQL - the Active-Version Predicate and the no-overlapping-PUBLISHED
 * exclusion constraint (brief §80), mirroring {@code ProcedureVersionRepositoryTest} exactly for
 * the questionnaire side.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class QuestionnaireVersionRepositoryTest {

  @Autowired private QuestionnaireRepository questionnaireRepository;
  @Autowired private QuestionnaireVersionRepository questionnaireVersionRepository;

  private Questionnaire newQuestionnaire(String prefix) {
    String code = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return questionnaireRepository.saveAndFlush(Questionnaire.create(code, "Test questionnaire"));
  }

  private QuestionnaireVersion publishedVersion(
      Questionnaire questionnaire, int versionNumber, LocalDate from, LocalDate to) {
    QuestionnaireVersion version =
        QuestionnaireVersion.draft(questionnaire, versionNumber, "Title", "Description", null);
    ReflectionTestUtils.setField(version, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(version, "effectiveFrom", from);
    ReflectionTestUtils.setField(version, "effectiveTo", to);
    return questionnaireVersionRepository.saveAndFlush(version);
  }

  @Test
  void findActivePublishedVersion_draftIsNeverReturned() {
    Questionnaire questionnaire = newQuestionnaire("TEST_DRAFT_ONLY");
    questionnaireVersionRepository.saveAndFlush(
        QuestionnaireVersion.draft(questionnaire, 1, "T", "D", null));

    Optional<QuestionnaireVersion> active =
        questionnaireVersionRepository.findActivePublishedVersion(
            questionnaire.getCode(), LocalDate.now());

    assertThat(active).isEmpty();
  }

  @Test
  void findActivePublishedVersion_outsideEffectiveRange_isNotReturned() {
    Questionnaire questionnaire = newQuestionnaire("TEST_EXPIRED");
    publishedVersion(questionnaire, 1, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1));

    Optional<QuestionnaireVersion> active =
        questionnaireVersionRepository.findActivePublishedVersion(
            questionnaire.getCode(), LocalDate.now());

    assertThat(active).isEmpty();
  }

  @Test
  void findActivePublishedVersion_withinRange_isReturned() {
    Questionnaire questionnaire = newQuestionnaire("TEST_ACTIVE");
    QuestionnaireVersion version =
        publishedVersion(questionnaire, 1, LocalDate.now().minusDays(1), null);

    Optional<QuestionnaireVersion> active =
        questionnaireVersionRepository.findActivePublishedVersion(
            questionnaire.getCode(), LocalDate.now());

    assertThat(active).contains(version);
  }

  @Test
  void twoOverlappingPublishedVersions_areRejectedByTheExclusionConstraint() {
    Questionnaire questionnaire = newQuestionnaire("TEST_OVERLAP");
    publishedVersion(questionnaire, 1, LocalDate.of(2026, 1, 1), null);

    QuestionnaireVersion overlapping =
        QuestionnaireVersion.draft(questionnaire, 2, "T2", "D2", null);
    ReflectionTestUtils.setField(overlapping, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(overlapping, "effectiveFrom", LocalDate.of(2026, 6, 1));

    assertThatThrownBy(() -> questionnaireVersionRepository.saveAndFlush(overlapping))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void versionNumberUniquePerQuestionnaire_isEnforced() {
    Questionnaire questionnaire = newQuestionnaire("TEST_DUP_VERSION");
    questionnaireVersionRepository.saveAndFlush(
        QuestionnaireVersion.draft(questionnaire, 1, "T", "D", null));

    QuestionnaireVersion duplicate = QuestionnaireVersion.draft(questionnaire, 1, "T2", "D2", null);
    assertThatThrownBy(() -> questionnaireVersionRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
