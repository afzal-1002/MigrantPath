package com.foreignerwarsaw.usercase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * User cases end to end against a real Testcontainers Postgres (brief §121/§125/§126/§137):
 * snapshot creation, personalization from real Phase 4 content (never hard-coded), progress,
 * idempotency, historical reproducibility across a procedure republish, requirement-change
 * detection, upgrade with status-preservation/NEEDS_UPDATE, and the ownership/validation boundary
 * (outdated recommendation, disallowed recommendation type, empty content). Only synthetic {@code
 * TEST_*} content is ever created here.
 */
class UserCaseIntegrationTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

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
  void fullLifecycle_snapshotCreationProgressReproducibilityAndUpgrade() throws Exception {
    LocalDate today = LocalDate.now(clock);
    AppUserPrincipal applicant = userWithRole("USER");

    // --- Procedure v1: 2 steps, 2 documents (1 mandatory, 1 conditional), 1 fee ---
    String procedureCode = uniqueCode("TEST_CASE_PROCEDURE");
    createProcedureVersion1(procedureCode);
    publishRule(
        procedureCode,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    String assessmentId = extractId(startAssessment(applicant));
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

    String recommendationId = analyzeAndGetRecommendationId(applicant, assessmentId, procedureCode);

    // --- Case creation: real snapshot from real Phase 4 content ---
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/recommendations/" + recommendationId + "/cases")
                    .with(user(applicant))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.procedureCode").value(procedureCode))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.steps.length()").value(2))
            .andExpect(jsonPath("$.documents.length()").value(2))
            .andExpect(jsonPath("$.fees.length()").value(1))
            .andReturn();
    JsonNode caseBody = objectMapper.readTree(created.getResponse().getContentAsString());
    String caseId = caseBody.get("id").asText();
    String mandatoryDocId = idOfDocument(caseBody, "TEST_DOC_MANDATORY");
    String conditionalDocId = idOfDocument(caseBody, "TEST_DOC_CONDITIONAL");
    String step1Id = idOfStep(caseBody, "TEST_STEP_1");

    assertThat(fieldOfDocument(caseBody, "TEST_DOC_MANDATORY", "applicability"))
        .isEqualTo("APPLICABLE");
    assertThat(fieldOfDocument(caseBody, "TEST_DOC_MANDATORY", "mandatory")).isEqualTo("true");
    assertThat(fieldOfDocument(caseBody, "TEST_DOC_CONDITIONAL", "applicability"))
        .isEqualTo("NEEDS_CONFIRMATION");
    assertThat(fieldOfDocument(caseBody, "TEST_DOC_CONDITIONAL", "mandatory")).isEqualTo("false");

    // --- Idempotency: a second POST for the same recommendation returns the same case ---
    mockMvc
        .perform(
            post("/api/v1/recommendations/" + recommendationId + "/cases")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(caseId));

    // --- Progress + checklist updates ---
    mockMvc
        .perform(
            patch("/api/v1/cases/" + caseId + "/steps/" + step1Id)
                .with(user(applicant))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.stepsCompleted").value(1));
    mockMvc
        .perform(
            patch("/api/v1/cases/" + caseId + "/documents/" + mandatoryDocId)
                .with(user(applicant))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"READY\",\"userNote\":\"Have the original\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.documentsReady").value(1))
        .andExpect(jsonPath("$.progress.conditionalDocumentsToReview").value(1));

    // --- Republish the procedure: step 1 title changes, conditional doc removed, a new doc added
    // ---
    createProcedureVersion2(procedureCode, today);

    // Reproducibility: the case still shows the ORIGINAL v1 content.
    mockMvc
        .perform(get("/api/v1/cases/" + caseId).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionNumber").value(1))
        .andExpect(jsonPath("$.hasRequirementUpdates").value(true))
        .andExpect(jsonPath("$.documents.length()").value(2));

    // Requirement-change detection.
    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/requirement-changes").with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newerVersionAvailable").value(true))
        .andExpect(
            jsonPath("$.changes[?(@.stableCode == 'TEST_STEP_1' && @.changeType == 'CHANGED')]")
                .exists())
        .andExpect(
            jsonPath(
                    "$.changes[?(@.stableCode == 'TEST_DOC_CONDITIONAL' && @.changeType == 'REMOVED')]")
                .exists())
        .andExpect(
            jsonPath("$.changes[?(@.stableCode == 'TEST_DOC_NEW' && @.changeType == 'ADDED')]")
                .exists());

    // --- Upgrade: new revision, progress preserved where unchanged, NEEDS_UPDATE where material
    // ---
    MvcResult upgraded =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/upgrade").with(user(applicant)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisionNumber").value(2))
            .andExpect(
                jsonPath("$.documents.length()").value(2)) // mandatory + new (conditional removed)
            .andReturn();
    JsonNode upgradedBody = objectMapper.readTree(upgraded.getResponse().getContentAsString());

    assertThat(fieldOfStep(upgradedBody, "TEST_STEP_1", "status")).isEqualTo("NOT_STARTED");
    assertThat(fieldOfDocument(upgradedBody, "TEST_DOC_MANDATORY", "status")).isEqualTo("READY");
    assertThat(fieldOfDocument(upgradedBody, "TEST_DOC_MANDATORY", "userNote"))
        .isEqualTo("Have the original");
    assertThat(hasDocument(upgradedBody, "TEST_DOC_CONDITIONAL")).isFalse();
    assertThat(fieldOfDocument(upgradedBody, "TEST_DOC_NEW", "status")).isEqualTo("NOT_STARTED");

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/requirement-changes").with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newerVersionAvailable").value(false));

    // --- Case history ---
    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/events").with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.eventType == 'CASE_CREATED')]").exists())
        .andExpect(jsonPath("$[?(@.eventType == 'CASE_UPDATED_TO_NEW_VERSION')]").exists());

    // --- My Cases list ---
    mockMvc
        .perform(get("/api/v1/cases").with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + caseId + "')]").exists());

    // --- Ownership / IDOR ---
    AppUserPrincipal intruder = userWithRole("USER");
    mockMvc
        .perform(get("/api/v1/cases/" + caseId).with(user(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/requirement-changes").with(user(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/v1/cases/" + caseId + "/upgrade").with(user(intruder)).with(csrf()))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/cases/" + caseId)).andExpect(status().isUnauthorized());
  }

  @Test
  void caseCreation_rejectsDisallowedRecommendationTypes_emptyContent_andOutdatedRecommendations()
      throws Exception {
    LocalDate today = LocalDate.now(clock);
    AppUserPrincipal applicant = userWithRole("USER");

    // A procedure with no steps at all - never case-ready even if the rule matches.
    String emptyProcedureCode = uniqueCode("TEST_EMPTY_PROCEDURE");
    createPublishedProcedureShell(emptyProcedureCode);
    publishRule(
        emptyProcedureCode,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    // A procedure whose rule requires a fact never answered - MORE_INFORMATION_REQUIRED.
    String moreInfoProcedureCode = uniqueCode("TEST_MORE_INFO_PROCEDURE");
    createProcedureVersion1(moreInfoProcedureCode);
    publishRule(
        moreInfoProcedureCode,
        "{\"fact\":\"MONTHLY_GROSS_SALARY\",\"operator\":\"GREATER_THAN\",\"value\":1000}");

    // A procedure we will republish out from under an unused recommendation.
    String outdatedProcedureCode = uniqueCode("TEST_OUTDATED_PROCEDURE");
    createProcedureVersion1(outdatedProcedureCode);
    publishRule(
        outdatedProcedureCode,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    String assessmentId = extractId(startAssessment(applicant));
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

    String emptyRecommendationId =
        analyzeAndGetRecommendationId(applicant, assessmentId, emptyProcedureCode);
    mockMvc
        .perform(
            post("/api/v1/recommendations/" + emptyRecommendationId + "/cases")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CASE_CONTENT_NOT_READY"));

    String moreInfoRecommendationId =
        analyzeAndGetRecommendationId(applicant, assessmentId, moreInfoProcedureCode);
    mockMvc
        .perform(
            post("/api/v1/recommendations/" + moreInfoRecommendationId + "/cases")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CASE_CREATION_NOT_ALLOWED"));

    String outdatedRecommendationId =
        analyzeAndGetRecommendationId(applicant, assessmentId, outdatedProcedureCode);
    createProcedureVersion2(outdatedProcedureCode, today);
    mockMvc
        .perform(
            post("/api/v1/recommendations/" + outdatedRecommendationId + "/cases")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION_OUTDATED"));
  }

  // --- helpers ---

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

  private String idOfStep(JsonNode caseBody, String stableCode) {
    for (JsonNode step : caseBody.get("steps")) {
      if (step.get("stableCode").asText().equals(stableCode)) {
        return step.get("id").asText();
      }
    }
    throw new AssertionError("No step " + stableCode);
  }

  private String idOfDocument(JsonNode caseBody, String stableCode) {
    for (JsonNode doc : caseBody.get("documents")) {
      if (doc.get("stableCode").asText().equals(stableCode)) {
        return doc.get("id").asText();
      }
    }
    throw new AssertionError("No document " + stableCode);
  }

  private boolean hasDocument(JsonNode caseBody, String stableCode) {
    for (JsonNode doc : caseBody.get("documents")) {
      if (doc.get("stableCode").asText().equals(stableCode)) {
        return true;
      }
    }
    return false;
  }

  private String fieldOfDocument(JsonNode caseBody, String stableCode, String field) {
    for (JsonNode doc : caseBody.get("documents")) {
      if (doc.get("stableCode").asText().equals(stableCode)) {
        return doc.get(field).asText();
      }
    }
    throw new AssertionError("No document " + stableCode);
  }

  private String fieldOfStep(JsonNode caseBody, String stableCode, String field) {
    for (JsonNode step : caseBody.get("steps")) {
      if (step.get("stableCode").asText().equals(stableCode)) {
        return step.get(field).asText();
      }
    }
    throw new AssertionError("No step " + stableCode);
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
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/documents")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_DOC_CONDITIONAL\",\"name\":\"Marriage certificate\",\"requirementType\":\"CONDITIONAL\",\"requiredByDefault\":false,\"sortOrder\":2}"))
        .andExpect(status().isCreated());

    Procedure procedure = procedureService.findByCode(code).orElseThrow();
    ProcedureVersion version = procedureVersionService.getByProcedureAndVersionNumber(procedure, 1);
    feeService.addFee(version, "TEST_FEE", FeeType.APPLICATION, new BigDecimal("340.00"), "PLN");

    // Published starting yesterday, not today, so a v2 published with effectiveFrom = today
    // (createProcedureVersion2 below) starts strictly after v1's own start date - the same
    // OVERLAPPING_PUBLISHED_VERSION constraint every other publish test in this codebase must
    // respect.
    publishProcedureVersion(code, 1, today().minusDays(1));
  }

  private void createPublishedProcedureShell(String code) throws Exception {
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
    publishProcedureVersion(code, 1, today());
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
    return "user-case-test-" + UUID.randomUUID() + "@example.com";
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
