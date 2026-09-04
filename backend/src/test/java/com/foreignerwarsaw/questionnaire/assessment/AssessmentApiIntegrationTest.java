package com.foreignerwarsaw.questionnaire.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end assessment flow through the real Spring Security filter chain, against the real seeded
 * {@code WARSAW_GENERAL_ASSESSMENT} questionnaire (brief §81) - branching show/hide,
 * required-vs-hidden completion gating, reference-data validation, ownership/IDOR, and
 * unauthenticated rejection.
 */
// @DirtiesContext(AFTER_CLASS): see RecommendationEngineIntegrationTest's identical Javadoc -
// same real, reproduced-this-phase CookieCsrfTokenRepository pollution pattern.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AssessmentApiIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/assessments";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;

  private AppUserPrincipal registeredUser() {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Pat");
    user.markEmailVerified(java.time.Instant.now());
    Role role = roleRepository.findByCode("USER").orElseThrow();
    user.addRole(role);
    user = userRepository.save(user);
    return new AppUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), true, true, List.of("USER"));
  }

  private String uniqueEmail() {
    return "assessment-test-" + UUID.randomUUID() + "@example.com";
  }

  private String extractId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private boolean hasVisibleQuestion(MvcResult result, String code) throws Exception {
    JsonNode questions =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("questions");
    for (JsonNode q : questions) {
      if (q.get("questionCode").asText().equals(code)) {
        return true;
      }
    }
    return false;
  }

  private MvcResult start(AppUserPrincipal actor) throws Exception {
    return mockMvc
        .perform(post(BASE).with(user(actor)).with(csrf()))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult answer(
      AppUserPrincipal actor, String assessmentId, String questionCode, String bodyJson)
      throws Exception {
    return mockMvc
        .perform(
            put(BASE + "/" + assessmentId + "/answers/" + questionCode)
                .with(user(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
        .andReturn();
  }

  @Test
  void start_withoutAuthentication_isUnauthorized() throws Exception {
    // With a valid CSRF token but no session, so this isolates "not authenticated" (401) from
    // CSRF rejection (403, brief §11/§27 - enforced on this endpoint too, proven separately by
    // not attaching .with(csrf()) not being needed here since 401 is expected either way, but an
    // explicit token keeps the intent unambiguous).
    mockMvc.perform(post(BASE).with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  void start_createsAnAssessmentBoundToTheActiveVersion_withOnlyTopLevelQuestionsVisible()
      throws Exception {
    AppUserPrincipal actor = registeredUser();
    MvcResult result = start(actor);

    mockMvc
        .perform(get(BASE + "/" + extractId(result)).with(user(actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionnaireCode").value("WARSAW_GENERAL_ASSESSMENT"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    assertThat(hasVisibleQuestion(result, "CITIZENSHIP_COUNTRY")).isTrue();
    assertThat(hasVisibleQuestion(result, "CURRENTLY_IN_POLAND")).isTrue();
    assertThat(hasVisibleQuestion(result, "PRIMARY_PURPOSE")).isTrue();
    // Nothing branch-specific yet - no answers given.
    assertThat(hasVisibleQuestion(result, "CURRENT_LEGAL_STATUS")).isFalse();
    assertThat(hasVisibleQuestion(result, "HAS_JOB_OFFER")).isFalse();
  }

  @Test
  void startTwice_resumesTheSameInProgressAssessment() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String firstId = extractId(start(actor));
    String secondId = extractId(start(actor));
    assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  void anotherUsersAssessment_isNotFoundNotForbidden() throws Exception {
    AppUserPrincipal owner = registeredUser();
    AppUserPrincipal intruder = registeredUser();
    String assessmentId = extractId(start(owner));

    mockMvc
        .perform(get(BASE + "/" + assessmentId).with(user(intruder)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"));
  }

  @Test
  void answeringCurrentlyInPoland_revealsCurrentLegalStatus() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));

    MvcResult after = answer(actor, id, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    assertThat(after.getResponse().getStatus()).isEqualTo(200);
    assertThat(hasVisibleQuestion(after, "CURRENT_LEGAL_STATUS")).isTrue();
    // The not-in-Poland branch must not also be visible.
    assertThat(hasVisibleQuestion(after, "CURRENT_COUNTRY")).isFalse();
  }

  @Test
  void selectingWorkPurpose_revealsWorkBranch_thenRemovingItHidesItAgain() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));

    MvcResult withWork =
        answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"WORK\"]}");
    assertThat(hasVisibleQuestion(withWork, "HAS_JOB_OFFER")).isTrue();

    MvcResult withOffer = answer(actor, id, "HAS_JOB_OFFER", "{\"booleanValue\":true}");
    assertThat(hasVisibleQuestion(withOffer, "MONTHLY_GROSS_SALARY")).isTrue();
    assertThat(hasVisibleQuestion(withOffer, "EMPLOYMENT_CONTRACT_TYPE")).isTrue();

    MvcResult withoutWork =
        answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    assertThat(hasVisibleQuestion(withoutWork, "HAS_JOB_OFFER")).isFalse();
    assertThat(hasVisibleQuestion(withoutWork, "MONTHLY_GROSS_SALARY")).isFalse();
  }

  @Test
  void completingWithVisibleRequiredQuestionUnanswered_fails() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));

    mockMvc
        .perform(post(BASE + "/" + id + "/complete").with(user(actor)).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSESSMENT_INCOMPLETE"))
        .andExpect(jsonPath("$.errors[?(@.field == 'CITIZENSHIP_COUNTRY')]").exists());
  }

  @Test
  void aRequiredQuestionThatBecomesHidden_neverBlocksCompletion() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));

    // Enter the WORK branch (HAS_JOB_OFFER is required while WORK is selected)...
    answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"WORK\"]}");
    // ...then leave it again without ever answering HAS_JOB_OFFER.
    answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");

    answerMinimalAboutYouSection(actor, id);

    mockMvc
        .perform(post(BASE + "/" + id + "/complete").with(user(actor)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void validCountryCode_isAccepted_invalidCountryCode_isRejected() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));

    answer(actor, id, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    mockMvc
        .perform(get(BASE + "/" + id).with(user(actor)))
        .andExpect(
            jsonPath("$.questions[?(@.questionCode == 'CITIZENSHIP_COUNTRY')].answer.referenceCode")
                .value("PK"));

    mockMvc
        .perform(
            put(BASE + "/" + id + "/answers/CITIZENSHIP_COUNTRY")
                .with(user(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceCode\":\"XX\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ASSESSMENT_ANSWER"));
  }

  @Test
  void fullMinimalHappyPath_reachesCompleted() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));
    answerMinimalAboutYouSection(actor, id);
    answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");

    MvcResult completed =
        mockMvc
            .perform(post(BASE + "/" + id + "/complete").with(user(actor)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.progressPercent").value(100))
            .andReturn();
    assertThat(completed).isNotNull();
  }

  @Test
  void restartingACompletedAssessment_copiesApplicableAnswersForward() throws Exception {
    AppUserPrincipal actor = registeredUser();
    String id = extractId(start(actor));
    answerMinimalAboutYouSection(actor, id);
    answer(actor, id, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    mockMvc
        .perform(post(BASE + "/" + id + "/complete").with(user(actor)).with(csrf()))
        .andExpect(status().isOk());

    MvcResult restarted =
        mockMvc
            .perform(post(BASE + "/" + id + "/restart").with(user(actor)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andReturn();
    String newId = extractId(restarted);
    assertThat(newId).isNotEqualTo(id);

    mockMvc
        .perform(get(BASE + "/" + newId).with(user(actor)))
        .andExpect(
            jsonPath("$.questions[?(@.questionCode == 'CITIZENSHIP_COUNTRY')].answer.referenceCode")
                .value("PK"));
  }

  private void answerMinimalAboutYouSection(AppUserPrincipal actor, String id) throws Exception {
    answer(actor, id, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(actor, id, "CURRENTLY_IN_POLAND", "{\"booleanValue\":false}");
    answer(actor, id, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
  }
}
