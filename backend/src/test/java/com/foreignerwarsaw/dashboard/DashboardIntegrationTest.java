package com.foreignerwarsaw.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureService;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionService;
import com.foreignerwarsaw.procedure.fee.FeeService;
import com.foreignerwarsaw.procedure.fee.FeeType;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionService;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.math.BigDecimal;
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
 * Post-MVP UX Milestone UX1 (redesign pass) - {@code GET /api/v1/dashboard} against a real
 * Testcontainers Postgres, exercising the same real-content path as {@code UserCaseIntegrationTest}
 * rather than mocking the aggregated services. Covers every user state the new dashboard UI must
 * render (brief §84's required test states) plus an explicit per-user isolation check (there is no
 * {@code userId} parameter to attack, so the isolation proof is simply that two different
 * principals never see each other's data).
 */
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardIntegrationTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";
  private static final String DASHBOARD = "/api/v1/dashboard";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private ProcedureService procedureService;
  @Autowired private ProcedureVersionService procedureVersionService;
  @Autowired private FeeService feeService;
  @Autowired private RuleService ruleService;
  @Autowired private RuleVersionService ruleVersionService;
  @Autowired private RulePublishingService rulePublishingService;
  @Autowired private OfficialSourceRepository officialSourceRepository;
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
  void newUser_dashboardShowsOnboardingStateWithNoFabricatedData() throws Exception {
    AppUserPrincipal newUser = userWithRole("USER");

    mockMvc
        .perform(get(DASHBOARD).with(user(newUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.city").value("Warsaw"))
        .andExpect(jsonPath("$.primaryCase").doesNotExist())
        .andExpect(jsonPath("$.activeCases.length()").value(0))
        .andExpect(jsonPath("$.importantDates.length()").value(0))
        .andExpect(jsonPath("$.completedMilestones.length()").value(0))
        .andExpect(jsonPath("$.latestRecommendations.length()").value(0))
        .andExpect(jsonPath("$.assessmentStatus").doesNotExist())
        .andExpect(jsonPath("$.nextActions[0].heading").value("Find the right pathway for you"))
        .andExpect(jsonPath("$.nextActions[0].severity").value("INFO"));

    mockMvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
  }

  @Test
  void assessmentInProgress_dashboardOffersToContinueIt() throws Exception {
    AppUserPrincipal applicant = userWithRole("USER");
    String assessmentId = extractId(startAssessment(applicant));
    answer(applicant, assessmentId, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");

    mockMvc
        .perform(get(DASHBOARD).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assessmentStatus.assessmentId").value(assessmentId))
        .andExpect(jsonPath("$.assessmentStatus.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.nextActions[?(@.heading == 'Continue your assessment')]").exists())
        .andExpect(
            jsonPath("$.nextActions[?(@.heading == 'Continue your assessment')].assessmentId")
                .value(assessmentId));
  }

  @Test
  void recommendationReady_dashboardSurfacesItWithoutACaseYet() throws Exception {
    AppUserPrincipal applicant = userWithRole("USER");
    String procedureCode = uniqueCode("TEST_DASH_RECOMMENDED");
    createProcedureVersion1(procedureCode);
    publishRule(
        procedureCode,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    String assessmentId = extractId(startAssessment(applicant));
    completeMinimalAssessment(applicant, assessmentId);
    analyzeAndGetRecommendationId(applicant, assessmentId, procedureCode);

    mockMvc
        .perform(get(DASHBOARD).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryCase").doesNotExist())
        .andExpect(jsonPath("$.assessmentStatus.status").value("COMPLETED"))
        .andExpect(
            jsonPath("$.latestRecommendations[?(@.procedureCode == '" + procedureCode + "')]")
                .exists())
        .andExpect(
            jsonPath("$.nextActions[?(@.heading == 'Your recommended pathway is ready')]")
                .exists());
  }

  @Test
  void activeCase_dashboardShowsPrimaryCaseProgressAndRealDatesOnly() throws Exception {
    AppUserPrincipal applicant = userWithRole("USER");
    String procedureCode = uniqueCode("TEST_DASH_ACTIVE_CASE");
    String caseId = createSimpleCase(applicant, procedureCode);

    mockMvc
        .perform(get(DASHBOARD).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryCase.id").value(caseId))
        .andExpect(jsonPath("$.primaryCase.procedureCode").value(procedureCode))
        .andExpect(jsonPath("$.primaryCase.stepsTotal").value(2))
        .andExpect(jsonPath("$.primaryCase.stageIndex").value(0))
        .andExpect(jsonPath("$.primaryCase.stageLabel").value("Started"))
        .andExpect(jsonPath("$.activeCases[?(@.id == '" + caseId + "')]").exists())
        .andExpect(jsonPath("$.importantDates[0].type").value("CASE_STARTED"))
        .andExpect(jsonPath("$.importantDates[?(@.type == 'CASE_SUBMITTED')]").doesNotExist())
        .andExpect(jsonPath("$.nextActions[?(@.heading == 'Continue your checklist')]").exists());
  }

  @Test
  void requirementUpdate_dashboardFlagsTheCaseAsNeedingAttentionFirst() throws Exception {
    AppUserPrincipal applicant = userWithRole("USER");
    LocalDate today = LocalDate.now(clock);
    String procedureCode = uniqueCode("TEST_DASH_REQ_UPDATE");
    String caseId = createSimpleCase(applicant, procedureCode);

    createProcedureVersion2(procedureCode, today);

    mockMvc
        .perform(get(DASHBOARD).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryCase.id").value(caseId))
        .andExpect(jsonPath("$.primaryCase.hasRequirementUpdates").value(true))
        .andExpect(jsonPath("$.nextActions[0].heading").value("Requirements have changed"))
        .andExpect(jsonPath("$.nextActions[0].severity").value("ATTENTION"));
  }

  @Test
  void dashboard_isIsolatedPerUser_neverLeaksAnotherUsersCaseOrRecommendations() throws Exception {
    AppUserPrincipal userA = userWithRole("USER");
    AppUserPrincipal userB = userWithRole("USER");
    String procedureCode = uniqueCode("TEST_DASH_ISOLATION");
    String caseId = createSimpleCase(userA, procedureCode);

    mockMvc
        .perform(get(DASHBOARD).with(user(userA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryCase.id").value(caseId));

    mockMvc
        .perform(get(DASHBOARD).with(user(userB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryCase").doesNotExist())
        .andExpect(jsonPath("$.activeCases.length()").value(0));
  }

  // --- helpers (mirroring UserCaseIntegrationTest's own real-content setup) ---

  private String createSimpleCase(AppUserPrincipal applicant, String procedurePrefix)
      throws Exception {
    createProcedureVersion1(procedurePrefix);
    publishRule(
        procedurePrefix,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    String assessmentId = extractId(startAssessment(applicant));
    completeMinimalAssessment(applicant, assessmentId);
    String recommendationId =
        analyzeAndGetRecommendationId(applicant, assessmentId, procedurePrefix);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/recommendations/" + recommendationId + "/cases")
                    .with(user(applicant))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private void completeMinimalAssessment(AppUserPrincipal applicant, String assessmentId)
      throws Exception {
    answer(applicant, assessmentId, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(applicant, assessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":false}");
    answer(applicant, assessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(applicant, assessmentId, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private String analyzeAndGetRecommendationId(
      AppUserPrincipal applicant, String assessmentId, String procedureCode) throws Exception {
    MvcResult analyzed =
        mockMvc
            .perform(
                post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                    .with(user(applicant))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    for (JsonNode rec : body.get("recommendations")) {
      if (rec.get("procedureCode").asText().equals(procedureCode)) {
        return rec.get("id").asText();
      }
    }
    throw new AssertionError("No recommendation found for " + procedureCode);
  }

  private void createProcedureVersion1(String code) throws Exception {
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Test procedure\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Test v1\",\"summary\":\"Test summary\",\"description\":\"Test description\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/steps")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP_1\",\"title\":\"Prepare documents\",\"description\":\"Gather everything\",\"stepType\":\"PREPARATION\",\"sortOrder\":1,\"mandatory\":true}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/steps")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP_2\",\"title\":\"Submit application\",\"description\":\"Go to the office\",\"stepType\":\"IN_PERSON_SUBMISSION\",\"sortOrder\":2,\"mandatory\":true}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/documents")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_DOC_MANDATORY\",\"name\":\"Passport\",\"requirementType\":\"DEFAULT_REQUIRED\",\"requiredByDefault\":true,\"sortOrder\":1}"))
        .andExpect(status().isCreated());

    Procedure procedure = procedureService.findByCode(code).orElseThrow();
    ProcedureVersion version = procedureVersionService.getByProcedureAndVersionNumber(procedure, 1);
    feeService.addFee(version, "TEST_FEE", FeeType.APPLICATION, new BigDecimal("340.00"), "PLN");

    publishProcedureVersion(code, 1, today().minusDays(1));
  }

  private void createProcedureVersion2(String code, LocalDate effectiveFrom) throws Exception {
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Test v2\",\"summary\":\"Updated summary\",\"description\":\"Updated description\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/2/steps")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP_1\",\"title\":\"Prepare documents (updated)\",\"description\":\"Gather everything\",\"stepType\":\"PREPARATION\",\"sortOrder\":1,\"mandatory\":true}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/2/steps")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP_2\",\"title\":\"Submit application\",\"description\":\"Go to the office\",\"stepType\":\"IN_PERSON_SUBMISSION\",\"sortOrder\":2,\"mandatory\":true}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/2/documents")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_DOC_MANDATORY\",\"name\":\"Passport\",\"requirementType\":\"DEFAULT_REQUIRED\",\"requiredByDefault\":true,\"sortOrder\":1}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/2/documents")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_DOC_NEW\",\"name\":\"Proof of insurance\",\"requirementType\":\"DEFAULT_REQUIRED\",\"requiredByDefault\":true,\"sortOrder\":2}"))
        .andExpect(status().isCreated());

    Procedure procedure = procedureService.findByCode(code).orElseThrow();
    ProcedureVersion version = procedureVersionService.getByProcedureAndVersionNumber(procedure, 2);
    feeService.addFee(version, "TEST_FEE", FeeType.APPLICATION, new BigDecimal("340.00"), "PLN");

    publishProcedureVersion(code, 2, effectiveFrom);
  }

  private void publishProcedureVersion(String code, int versionNumber, LocalDate effectiveFrom)
      throws Exception {
    String sourceId = createAndVerifySource();
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/" + versionNumber + "/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/" + versionNumber + "/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/" + versionNumber + "/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/" + versionNumber + "/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + effectiveFrom + "\"}"))
        .andExpect(status().isOk());
  }

  private void publishRule(String procedureCode, String conditionTree) throws Exception {
    Rule rule =
        ruleService.createRule(
            procedureCode + "_RULE",
            "Test rule for " + procedureCode,
            RuleType.ELIGIBILITY,
            RuleTargetType.PROCEDURE,
            procedureCode);
    RuleVersion version =
        ruleVersionService.createDraft(
            rule, conditionTree, "rules.test." + procedureCode, actorEntity(editor));
    OfficialSource source =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    ruleVersionService.attachSource(version, source, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(version.getId(), actorEntity(editor));
    ruleVersionService.approve(version.getId(), actorEntity(reviewer));
    rulePublishingService.publish(version.getId(), actorEntity(admin), today());
  }

  private String createAndVerifySource() throws Exception {
    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(CONTENT_BASE + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Test source\",\"sourceUrl\":\"https://example.gov.pl/"
                                + UUID.randomUUID()
                                + "\",\"sourceType\":\"LEGISLATION\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\",\"notes\":\"Checked for this test\"}"))
        .andExpect(status().isOk());
    return sourceId;
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private MvcResult startAssessment(AppUserPrincipal actor) throws Exception {
    return mockMvc
        .perform(post(ASSESSMENTS_BASE).with(user(actor)).with(csrf()))
        .andExpect(status().isOk())
        .andReturn();
  }

  private void answer(
      AppUserPrincipal actor, String assessmentId, String questionCode, String bodyJson)
      throws Exception {
    mockMvc
        .perform(
            put(ASSESSMENTS_BASE + "/" + assessmentId + "/answers/" + questionCode)
                .with(user(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
        .andExpect(status().isOk());
  }

  private User actorEntity(AppUserPrincipal principal) {
    return userRepository.findById(principal.getUserId()).orElseThrow();
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
    return "dashboard-test-" + UUID.randomUUID() + "@example.com";
  }

  private String uniqueCode(String prefix) {
    return prefix
        + "_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
  }

  private String extractId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
