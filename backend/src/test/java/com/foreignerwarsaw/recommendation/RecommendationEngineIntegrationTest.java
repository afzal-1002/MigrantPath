package com.foreignerwarsaw.recommendation;

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
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdValueType;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import com.foreignerwarsaw.rules.core.RuleVersionService;
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
 * The recommendation engine end to end against a real Testcontainers Postgres (Recommendation
 * Engine brief §125-§128): classification into every {@code RecommendationType}, the {@code
 * PARTIAL} run-status/{@code UNAVAILABLE_FOR_ANALYSIS} path, immutable historical reproducibility
 * across a rule republish, the completed-assessment gate, and the recommendation endpoints'
 * ownership/IDOR boundary. Only synthetic {@code TEST_*} content is ever created here.
 */
// @DirtiesContext(AFTER_CLASS): canonical Phase 12 finding - this class's with(user())/
// with(csrf()) MockMvc-postprocessor style otherwise leaves shared CookieCsrfTokenRepository
// state that breaks a real-cookie-flow class running later in the same cached context (the same
// pollution AdminGovernanceIntegrationTest's own Javadoc documents), reproduced deterministically
// against a full ./mvnw verify run this phase, not a one-off flake.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationEngineIntegrationTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private RuleService ruleService;
  @Autowired private RuleVersionService ruleVersionService;
  @Autowired private RuleVersionRepository ruleVersionRepository;
  @Autowired private RulePublishingService rulePublishingService;
  @Autowired private ThresholdService thresholdService;
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
  void fullLifecycle_classifiesEveryCategory_partialStatusOnError_andRemainsReproducible()
      throws Exception {
    LocalDate today = LocalDate.now(clock);
    AppUserPrincipal applicant = userWithRole("USER");

    // --- Four synthetic procedures, one per RecommendationType ---
    String matchProcedure = createPublishedProcedure("TEST_MATCH_PROCEDURE");
    String moreInfoProcedure = createPublishedProcedure("TEST_MORE_INFO_PROCEDURE");
    String notApplicableProcedure = createPublishedProcedure("TEST_NOT_APPLICABLE_PROCEDURE");
    String unavailableProcedure = createPublishedProcedure("TEST_UNAVAILABLE_PROCEDURE");

    // PRIMARY_MATCH: both conditions hold for a Pakistani citizen who selected GET_PESEL.
    publishRule(
        matchProcedure,
        RuleType.ELIGIBILITY,
        """
        {"all":[
          {"code":"NOT_EU","fact":"CITIZENSHIP_COUNTRY","operator":"IS_NOT_MEMBER_OF_COUNTRY_GROUP","value":"EU_MEMBER"},
          {"code":"WANTS_PESEL","fact":"PRIMARY_PURPOSE","operator":"CONTAINS","value":"GET_PESEL"}
        ]}
        """);

    // MORE_INFORMATION_REQUIRED: requires a fact never answered in the minimal path.
    publishRule(
        moreInfoProcedure,
        RuleType.REQUIREMENT,
        """
        {"fact":"MONTHLY_GROSS_SALARY","operator":"GREATER_THAN","value":1000}
        """);

    // NOT_APPLICABLE: an exclusion that is satisfied for this exact citizenship.
    publishRule(
        notApplicableProcedure,
        RuleType.EXCLUSION,
        """
        {"fact":"CITIZENSHIP_COUNTRY","operator":"EQUALS","value":"PK"}
        """);

    // UNAVAILABLE_FOR_ANALYSIS: references a real Threshold with no active PUBLISHED version -
    // DATE_OF_BIRTH (always answered in the minimal completion path below) is deliberately
    // used here rather than an unanswered fact, so this scenario exercises the "threshold
    // config problem" ERROR path specifically, not the unrelated "fact missing" MISSING path.
    Threshold unpublishedThreshold =
        thresholdService.createThreshold(
            uniqueCode("TEST_UNPUBLISHED_THRESHOLD"),
            "Never-published test threshold",
            ThresholdValueType.INTEGER);
    publishRule(
        unavailableProcedure,
        RuleType.ELIGIBILITY,
        "{\"fact\":\"DATE_OF_BIRTH\",\"operator\":\"DATE_BEFORE\",\"threshold\":\"%s\"}"
            .formatted(unpublishedThreshold.getCode()));

    // --- Complete a real assessment through the real HTTP + Security stack ---
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

    // --- Analyze: one recommendation per category, run status PARTIAL ---
    MvcResult analyzed =
        mockMvc
            .perform(
                post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                    .with(user(applicant))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIAL"))
            .andReturn();
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    String runId = body.get("id").asText();

    assertThat(typeFor(body, matchProcedure)).isEqualTo("PRIMARY_MATCH");
    assertThat(typeFor(body, moreInfoProcedure)).isEqualTo("MORE_INFORMATION_REQUIRED");
    assertThat(missingFactsFor(body, moreInfoProcedure)).contains("MONTHLY_GROSS_SALARY");
    assertThat(typeFor(body, notApplicableProcedure)).isEqualTo("NOT_APPLICABLE");
    assertThat(typeFor(body, unavailableProcedure)).isEqualTo("UNAVAILABLE_FOR_ANALYSIS");

    // The PRIMARY_MATCH recommendation carries matched-condition reasons, never a raw trace.
    JsonNode matchRec = recommendationFor(body, matchProcedure);
    assertThat(matchRec.get("reasons"))
        .anySatisfy(r -> assertThat(r.get("reasonType").asText()).isEqualTo("MATCHED_CONDITION"));

    // --- Historical reproducibility: republishing a new rule version never changes run 1 ---
    Rule matchRule = ruleService.getByCode(ruleCodeFor(matchProcedure));
    RuleVersion v1 =
        ruleVersionRepository.findActivePublishedVersion(matchRule.getId(), today).orElseThrow();
    RuleVersion v2 = ruleVersionService.createDraftFrom(v1, actorEntity(editor));
    ruleVersionService.updateDraftContent(
        v2.getId(),
        // Now requires an impossible fact - the same applicant no longer matches.
        "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"EQUALS\",\"value\":\"ZZ\"}",
        "rules.test.match.v2");
    OfficialSource source2 =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    ruleVersionService.attachSource(v2, source2, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(v2.getId(), actorEntity(editor));
    ruleVersionService.approve(v2.getId(), actorEntity(reviewer));
    LocalDate futureDate = today.plusMonths(1);
    rulePublishingService.publish(v2.getId(), actorEntity(admin), futureDate);

    // Re-reading the ORIGINAL run still shows the original PRIMARY_MATCH classification.
    mockMvc
        .perform(get("/api/v1/recommendation-runs/" + runId).with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.recommendations[?(@.procedureCode == '"
                        + matchProcedure
                        + "')].recommendationType")
                .value("PRIMARY_MATCH"));

    // A fresh analysis at the future date now sees v2 and no longer matches.
    MvcResult reanalyzed =
        mockMvc
            .perform(
                post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                    .with(user(applicant))
                    .with(csrf())
                    .param("evaluationDate", futureDate.toString()))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode reanalyzedBody = objectMapper.readTree(reanalyzed.getResponse().getContentAsString());
    assertThat(reanalyzedBody.get("id").asText()).isNotEqualTo(runId);
    assertThat(typeFor(reanalyzedBody, matchProcedure)).isNotEqualTo("PRIMARY_MATCH");

    // --- History lists both runs, most recent first ---
    mockMvc
        .perform(
            get(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                .with(user(applicant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    // --- Ownership / IDOR ---
    AppUserPrincipal intruder = userWithRole("USER");
    mockMvc
        .perform(get("/api/v1/recommendation-runs/" + runId).with(user(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendations/latest")
                .with(user(intruder)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                .with(user(intruder))
                .with(csrf()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/recommendation-runs/" + runId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void analyze_rejectsAnInProgressAssessment() throws Exception {
    AppUserPrincipal applicant = userWithRole("USER");
    String assessmentId = extractId(startAssessment(applicant));

    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_COMPLETED"));
  }

  /**
   * Canonical Phase 11 (Testing Completeness) brief §53 - "Temp Residence Studies": the exact
   * real-world situation `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` documents for {@code
   * TEMP_RESIDENCE_STUDY} (its Rules are {@code APPROVED}, its Procedure is {@code
   * READY_FOR_PUBLICATION} - neither published) must never leak a recommendation. This generalizes
   * it: a rule already {@code PUBLISHED} and actively targeting a procedure whose own content has
   * never been published at all (the harder, more realistic ordering - governance could plausibly
   * approve+publish a Rule slightly ahead of its Procedure) must still produce {@code
   * UNAVAILABLE_FOR_ANALYSIS}, never a confident match, and must still be rejected at case creation
   * - proving the two publication gates (Procedure's own {@code PUBLISHED} version, independent of
   * whatever its Rules say) are genuinely decoupled and both required, not that this only happens
   * to work today because the real content also stayed in lockstep.
   */
  @Test
  void ruleTargetingAnUnpublishedProcedure_isUnavailableForAnalysis_neverLeaksAConfidentMatch()
      throws Exception {
    String procedureCode = createUnpublishedProcedure("TEST_UNPUBLISHED_TARGET");
    publishRule(
        procedureCode,
        RuleType.APPLICABILITY,
        "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}");

    AppUserPrincipal applicant = userWithRole("USER");
    String assessmentId = extractId(startAssessment(applicant));
    answer(applicant, assessmentId, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(applicant, assessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(applicant, assessmentId, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(applicant, assessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(applicant, assessmentId, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isOk());

    MvcResult analyzed =
        mockMvc
            .perform(
                post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                    .with(user(applicant))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    assertThat(typeFor(body, procedureCode)).isEqualTo("UNAVAILABLE_FOR_ANALYSIS");

    // And case creation for it is rejected outright - never allowed just because a rule matched.
    String recommendationId = recommendationFor(body, procedureCode).get("id").asText();
    mockMvc
        .perform(
            post("/api/v1/recommendations/" + recommendationId + "/cases")
                .with(user(applicant))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CASE_CREATION_NOT_ALLOWED"));
  }

  /**
   * A Procedure that reaches DRAFT content but is deliberately never published - mirrors the real
   * TEMP_RESIDENCE_STUDY situation exactly (Procedure held at READY_FOR_PUBLICATION).
   */
  private String createUnpublishedProcedure(String prefix) throws Exception {
    String code = uniqueCode(prefix);
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
    return code;
  }

  private String typeFor(JsonNode runBody, String procedureCode) {
    return recommendationFor(runBody, procedureCode).get("recommendationType").asText();
  }

  private List<String> missingFactsFor(JsonNode runBody, String procedureCode) {
    JsonNode missing = recommendationFor(runBody, procedureCode).get("missingFacts");
    return objectMapper.convertValue(missing, List.class);
  }

  private JsonNode recommendationFor(JsonNode runBody, String procedureCode) {
    for (JsonNode rec : runBody.get("recommendations")) {
      if (rec.get("procedureCode").asText().equals(procedureCode)) {
        return rec;
      }
    }
    throw new AssertionError("No recommendation found for " + procedureCode);
  }

  private String ruleCodeFor(String procedureCode) {
    return procedureCode + "_RULE";
  }

  private void publishRule(String procedureCode, RuleType ruleType, String conditionTree)
      throws Exception {
    Rule rule =
        ruleService.createRule(
            ruleCodeFor(procedureCode),
            "Test rule for " + procedureCode,
            ruleType,
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
    rulePublishingService.publish(version.getId(), actorEntity(admin), LocalDate.now(clock));
  }

  private String createPublishedProcedure(String prefix) throws Exception {
    String code = uniqueCode(prefix);
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
    String sourceId = createAndVerifySource();
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(CONTENT_BASE + "/procedures/" + code + "/versions/1/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
        .andExpect(status().isOk());
    return code;
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
    return "recommendation-test-" + UUID.randomUUID() + "@example.com";
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
