package com.foreignerwarsaw.questionnaire.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentQueryService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentService;
import com.foreignerwarsaw.questionnaire.assessment.dto.AssessmentDetailResponse;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * brief §44/§81's "immutable questionnaire version" scenario: publishing a new {@link
 * QuestionnaireVersion} must never retroactively change what an already-{@code IN_PROGRESS} (or
 * completed) {@link Assessment} sees, and a {@code PUBLISHED} version's own content mutators must
 * refuse to run.
 *
 * <p>Uses its own freshly-created {@code Questionnaire} per test method (never the shared seeded
 * {@code WARSAW_GENERAL_ASSESSMENT}) - these tests publish multiple versions on the same calendar
 * day, which would otherwise collide with each other (and with the seeded questionnaire's own
 * effective-date range) since this codebase's integration tests don't roll back per-method.
 */
class QuestionnaireVersionImmutabilityIntegrationTest extends AbstractIntegrationTest {

  @Autowired private QuestionnaireRepository questionnaireRepository;
  @Autowired private QuestionnaireVersionRepository questionnaireVersionRepository;
  @Autowired private QuestionnaireVersionService questionnaireVersionService;
  @Autowired private QuestionnaireQueryService questionnaireQueryService;
  @Autowired private AssessmentService assessmentService;
  @Autowired private AssessmentQueryService assessmentQueryService;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;

  private User testUser() {
    User user =
        User.newRegistration(
            "qv-immutability-" + UUID.randomUUID() + "@example.com", "hash", "Pat");
    user.markEmailVerified(java.time.Instant.now());
    Role role = roleRepository.findByCode("USER").orElseThrow();
    user.addRole(role);
    return userRepository.save(user);
  }

  /**
   * A published v1 of a brand-new, test-only questionnaire, effective since well in the past - so
   * every test method's own {@code LocalDate.now()} publish never collides with it.
   */
  private QuestionnaireVersion newPublishedQuestionnaireV1() {
    Questionnaire questionnaire =
        questionnaireRepository.saveAndFlush(
            Questionnaire.create(
                "TEST_QNR_IMMUT_" + UUID.randomUUID().toString().substring(0, 8), "Test"));
    QuestionnaireVersion v1 = QuestionnaireVersion.draft(questionnaire, 1, "V1", "V1", null);
    ReflectionTestUtils.setField(v1, "status", PublicationStatus.PUBLISHED);
    ReflectionTestUtils.setField(v1, "effectiveFrom", LocalDate.now().minusYears(1));
    return questionnaireVersionRepository.saveAndFlush(v1);
  }

  /**
   * DRAFT can't jump straight to PUBLISHED (brief §8's "no privileged skip-a-step override") -
   * every version this test publishes must walk DRAFT -> IN_REVIEW -> APPROVED first.
   */
  private QuestionnaireVersion advanceToApproved(QuestionnaireVersion draft) {
    draft.submitForReview(null, java.time.Instant.now());
    draft.approve(null, java.time.Instant.now());
    return questionnaireVersionRepository.saveAndFlush(draft);
  }

  @Test
  void publishingANewVersionDoesNotAffectAnAssessmentAlreadyBoundToTheOldOne() {
    QuestionnaireVersion v1 = newPublishedQuestionnaireV1();
    String questionnaireCode = v1.getQuestionnaire().getCode();
    Assessment assessment = assessmentService.start(testUser(), questionnaireCode);
    assertThat(assessment.getQuestionnaireVersion().getId()).isEqualTo(v1.getId());

    QuestionnaireVersion v2 =
        advanceToApproved(
            questionnaireVersionService.createDraftFrom(
                v1, v1.getTitle(), v1.getDescription(), null));
    questionnaireVersionService.publish(v2.getId(), null, LocalDate.now());

    QuestionnaireVersion nowActive =
        questionnaireQueryService.resolveActiveVersion(questionnaireCode);
    assertThat(nowActive.getId()).isEqualTo(v2.getId());
    assertThat(nowActive.getId()).isNotEqualTo(v1.getId());

    // The in-progress assessment, started before v2 existed, must still be bound to v1.
    Assessment reloaded =
        assessmentService.getOwned(assessment.getId(), assessment.getUser().getId());
    assertThat(reloaded.getQuestionnaireVersion().getId()).isEqualTo(v1.getId());

    AssessmentDetailResponse detail = assessmentQueryService.toDetailResponse(reloaded);
    assertThat(detail.questionnaireVersionId()).isEqualTo(v1.getId());
  }

  @Test
  void startingAFreshAssessmentAfterAPublish_bindsToTheNewVersion() {
    QuestionnaireVersion v1 = newPublishedQuestionnaireV1();
    String questionnaireCode = v1.getQuestionnaire().getCode();

    QuestionnaireVersion v2 =
        advanceToApproved(
            questionnaireVersionService.createDraftFrom(
                v1, v1.getTitle(), v1.getDescription(), null));
    questionnaireVersionService.publish(v2.getId(), null, LocalDate.now());

    Assessment freshAssessment = assessmentService.start(testUser(), questionnaireCode);
    assertThat(freshAssessment.getQuestionnaireVersion().getId()).isEqualTo(v2.getId());
  }

  @Test
  void publishedVersionContent_cannotBeMutatedDirectly() {
    QuestionnaireVersion v1 = newPublishedQuestionnaireV1();
    assertThatThrownBy(() -> v1.updateDraftContent("New title", "New description"))
        .isInstanceOf(IllegalStateException.class);
  }
}
