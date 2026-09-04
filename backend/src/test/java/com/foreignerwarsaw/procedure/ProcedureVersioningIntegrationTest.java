package com.foreignerwarsaw.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.procedure.core.ProcedureQueryService;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The full create-draft-through-publish lifecycle, through the real HTTP + Spring Security stack
 * (brief §80 - "one of the most important Phase 4 tests"), using synthetic {@code TEST_*} content
 * only (brief §54/§116) - no real legal content is ever created by this test. Three distinct
 * accounts (editor/reviewer/admin) exercise the CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN role split
 * (brief §44/§46) - the creator never approves or publishes their own content.
 */
// @DirtiesContext(AFTER_CLASS): see RecommendationEngineIntegrationTest's identical Javadoc -
// same real, reproduced-this-phase CookieCsrfTokenRepository pollution pattern.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProcedureVersioningIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/internal/content";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private ProcedureQueryService procedureQueryService;
  @Autowired private Clock clock;

  private AppUserPrincipal editor;
  private AppUserPrincipal reviewer;
  private AppUserPrincipal admin;

  @BeforeEach
  void setUpActors() {
    editor = userWithRole("CONTENT_EDITOR");
    reviewer = userWithRole("LEGAL_REVIEWER");
    admin = userWithRole("ADMIN");
  }

  @Test
  void fullLifecycle_createThroughPublish_thenPublicApiReturnsIt() throws Exception {
    String procedureCode = uniqueCode("TEST_PROCEDURE");

    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"%s","categoryCode":"OTHER","canonicalName":"Test Procedure","shortDescription":"For automated tests only","jurisdictionScope":"NATIONAL"}
                    """
                        .formatted(procedureCode)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Test Procedure v1\",\"summary\":\"Test summary\",\"description\":\"Test description\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"));

    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/steps")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP\",\"title\":\"Test step\",\"description\":\"Do the thing\",\"stepType\":\"PREPARATION\",\"sortOrder\":1,\"mandatory\":true}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/documents")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_DOC\",\"name\":\"Test document\",\"requirementType\":\"DEFAULT_REQUIRED\",\"requiredByDefault\":true,\"sortOrder\":1}"))
        .andExpect(status().isCreated());

    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(BASE + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Test source\",\"sourceUrl\":\"https://example.gov.pl/test-source\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    // Publishing must fail without a VERIFIED source (brief §28/§81) - proven before
    // verifying, not assumed.
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());

    // Reviewer verifies the source - a different account than the one that created it
    // (brief §46).
    mockMvc
        .perform(
            post(BASE + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\",\"notes\":\"Checked for this test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_REVIEW"));

    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // Clock.systemUTC() (AppConfig), not the JVM's local timezone (brief §36) - this
    // machine runs CEST (UTC+2), so a raw LocalDate.now() is already "tomorrow" relative
    // to the server's own Active-Version Predicate for roughly two hours after local
    // midnight, which made the GET below flakily 404 (found via a real failure, not a
    // hypothetical - see PHASE_5_REPORT.md's "Bugs Found").
    LocalDate today = LocalDate.now(clock);
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/1/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + today + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    // The real point of this test: the public, unauthenticated API now returns it.
    mockMvc
        .perform(get("/api/v1/procedures/" + procedureCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(procedureCode))
        .andExpect(jsonPath("$.steps[0].code").value("TEST_STEP"))
        .andExpect(jsonPath("$.documents[0].code").value("TEST_DOC"))
        .andExpect(jsonPath("$.sources[0].title").value("Test source"));

    mockMvc
        .perform(get("/api/v1/procedures"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == '" + procedureCode + "')]").exists());

    // --- Version 2, future-dated: brief §80's exact scenario ---
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Test Procedure v2\",\"summary\":\"Updated summary\",\"description\":\"Updated description\"}"))
        .andExpect(status().isCreated());

    String source2Id =
        extractId(
            mockMvc
                .perform(
                    post(BASE + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Test source v2\",\"sourceUrl\":\"https://example.gov.pl/test-source-v2\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(BASE + "/sources/" + source2Id + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/2/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(source2Id)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/2/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/2/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());

    LocalDate futureDate = today.plusMonths(1);
    mockMvc
        .perform(
            post(BASE + "/procedures/" + procedureCode + "/versions/2/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + futureDate + "\"}"))
        .andExpect(status().isOk());

    // Current public GET still resolves to Version 1 - Version 2 isn't effective yet.
    mockMvc
        .perform(get("/api/v1/procedures/" + procedureCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Procedure"))
        .andExpect(jsonPath("$.versionNumber").value(1));

    // Evaluated at the future effective date, Version 2 is now active (service-level
    // check, brief §39 - no public endpoint accepts an arbitrary evaluation date).
    var futureDetail = procedureQueryService.getPublishedDetail(procedureCode, futureDate);
    assertThat(futureDetail.versionNumber()).isEqualTo(2);
    assertThat(futureDetail.summary()).isEqualTo("Updated summary");
  }

  private AppUserPrincipal userWithRole(String roleCode) {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    user.markEmailVerified(java.time.Instant.now());
    Role role = roleRepository.findByCode(roleCode).orElseThrow();
    user.addRole(role);
    user = userRepository.save(user);
    return new AppUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), true, true, List.of(roleCode));
  }

  private String uniqueEmail() {
    return "procedure-test-" + UUID.randomUUID() + "@example.com";
  }

  private String uniqueCode(String prefix) {
    return prefix
        + "_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private String extractId(MvcResult result) throws Exception {
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("id").asText();
  }
}
