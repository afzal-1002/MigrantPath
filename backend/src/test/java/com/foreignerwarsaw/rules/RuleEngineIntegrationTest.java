package com.foreignerwarsaw.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdValueType;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleThresholdReference;
import com.foreignerwarsaw.rules.core.RuleThresholdReferenceRepository;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionService;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationService;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationStatus;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The rules engine end to end against a real Testcontainers Postgres (brief §99/§100): the
 * create-draft-through-publish lifecycle (source-gated, mirroring {@code
 * ProcedureVersioningIntegrationTest}), a real {@code rule_threshold_references} row, real
 * threshold and country-group resolution against actually-seeded reference data (Germany/{@code
 * EU_MEMBER}, brief §41), the future-dated-version exclusion-constraint race (brief §80's scenario
 * replayed for {@code RuleVersion}), and the {@link
 * com.foreignerwarsaw.rules.evaluation.RuleEvaluationController} IDOR test. Only synthetic {@code
 * TEST_*} rule content is ever created here (brief §54/§116) - no real legal claim.
 */
// @DirtiesContext(AFTER_CLASS): see RecommendationEngineIntegrationTest's identical Javadoc -
// same real, reproduced-this-phase CookieCsrfTokenRepository pollution pattern.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RuleEngineIntegrationTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private RuleService ruleService;
  @Autowired private RuleVersionService ruleVersionService;
  @Autowired private RulePublishingService rulePublishingService;
  @Autowired private RuleEvaluationService ruleEvaluationService;
  @Autowired private RuleThresholdReferenceRepository ruleThresholdReferenceRepository;
  @Autowired private OfficialSourceRepository officialSourceRepository;
  @Autowired private ThresholdService thresholdService;
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
  void fullLifecycle_publishesAThresholdBackedCountryAwareRule_andEvaluatesItAgainstRealFacts()
      throws Exception {
    LocalDate today = LocalDate.now(clock);

    // --- A real, published Threshold this rule will reference ---
    Threshold threshold =
        thresholdService.createThreshold(
            uniqueCode("TEST_SALARY_THRESHOLD"), "Test salary threshold", ThresholdValueType.MONEY);
    ThresholdVersion thresholdVersion =
        thresholdService.createDraftVersion(
            threshold, new BigDecimal("15000"), null, actorEntity(admin));
    // Pre-Phase-10 hardening (brief §D): a threshold version cannot publish without a VERIFIED
    // primary source, mirroring Procedure/Rule - see docs/admin/OFFICIAL_SOURCE_SAFETY.md.
    OfficialSource thresholdSource =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    thresholdService.attachSource(thresholdVersion, thresholdSource, SourceRole.PRIMARY);
    thresholdService.submitForReview(thresholdVersion.getId(), actorEntity(admin));
    thresholdService.approve(thresholdVersion.getId(), actorEntity(admin));
    thresholdService.publish(thresholdVersion.getId(), actorEntity(admin), today);

    // --- Rule + draft version: salary above threshold AND not an EU_MEMBER citizen ---
    String procedureTargetCode = uniqueCode("TEST_BLUE_CARD");
    Rule rule =
        ruleService.createRule(
            uniqueCode("TEST_RULE"),
            "Test eligibility rule",
            RuleType.ELIGIBILITY,
            RuleTargetType.PROCEDURE,
            procedureTargetCode);
    String conditionTree =
        """
        {"all":[
          {"fact":"MONTHLY_GROSS_SALARY","operator":"GREATER_THAN_OR_EQUAL","threshold":"%s"},
          {"fact":"CITIZENSHIP_COUNTRY","operator":"IS_NOT_MEMBER_OF_COUNTRY_GROUP","value":"EU_MEMBER"}
        ]}
        """
            .formatted(threshold.getCode());
    RuleVersion version =
        ruleVersionService.createDraft(
            rule, conditionTree, "rules.test.eligibility", actorEntity(editor));

    OfficialSource source =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    ruleVersionService.attachSource(version, source, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(version.getId(), actorEntity(editor));
    ruleVersionService.approve(version.getId(), actorEntity(reviewer));
    RuleVersion published =
        rulePublishingService.publish(version.getId(), actorEntity(admin), today);

    assertThat(published.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(ruleThresholdReferenceRepository.findByRuleVersion_Id(version.getId()))
        .extracting(RuleThresholdReference::getThresholdCode)
        .containsExactly(threshold.getCode());

    // A Pakistani citizen earning above the threshold satisfies both conditions.
    AssessmentFacts nonEuHighEarner =
        facts(
            today,
            Map.of("MONTHLY_GROSS_SALARY", new BigDecimal("16000"), "CITIZENSHIP_COUNTRY", "PK"));
    List<RuleEvaluationResult> nonEuResults =
        ruleEvaluationService.evaluateRulesForProcedure(
            procedureTargetCode, nonEuHighEarner, today);
    assertThat(nonEuResults).hasSize(1);
    assertThat(nonEuResults.get(0).status()).isEqualTo(RuleEvaluationStatus.SATISFIED);
    assertThat(nonEuResults.get(0).thresholdsUsed()).hasSize(1);
    assertThat(nonEuResults.get(0).thresholdsUsed().get(0).thresholdCode())
        .isEqualTo(threshold.getCode());

    // A German citizen (really-seeded EU_MEMBER, brief §41) fails the country-group leaf even
    // though the salary condition alone would pass.
    AssessmentFacts euHighEarner =
        facts(
            today,
            Map.of("MONTHLY_GROSS_SALARY", new BigDecimal("16000"), "CITIZENSHIP_COUNTRY", "DE"));
    List<RuleEvaluationResult> euResults =
        ruleEvaluationService.evaluateRulesForProcedure(procedureTargetCode, euHighEarner, today);
    assertThat(euResults.get(0).status()).isEqualTo(RuleEvaluationStatus.NOT_SATISFIED);

    // A non-EU citizen with no salary answer yet is INDETERMINATE, never a false FAIL.
    AssessmentFacts noSalaryYet = facts(today, Map.of("CITIZENSHIP_COUNTRY", "PK"));
    List<RuleEvaluationResult> missingResults =
        ruleEvaluationService.evaluateRulesForProcedure(procedureTargetCode, noSalaryYet, today);
    assertThat(missingResults.get(0).status()).isEqualTo(RuleEvaluationStatus.INDETERMINATE);
    assertThat(missingResults.get(0).missingFacts()).containsExactly("MONTHLY_GROSS_SALARY");

    // --- Version 2, future-dated: the exclusion-constraint race, replayed for RuleVersion ---
    RuleVersion v2 = ruleVersionService.createDraftFrom(published, actorEntity(editor));
    OfficialSource source2 =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    ruleVersionService.attachSource(v2, source2, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(v2.getId(), actorEntity(editor));
    ruleVersionService.approve(v2.getId(), actorEntity(reviewer));
    LocalDate futureDate = today.plusMonths(1);
    rulePublishingService.publish(v2.getId(), actorEntity(admin), futureDate);

    // Evaluating "today" still resolves to v1 - v2 isn't effective yet.
    List<RuleEvaluationResult> stillV1 =
        ruleEvaluationService.evaluateRulesForProcedure(
            procedureTargetCode, nonEuHighEarner, today);
    assertThat(stillV1.get(0).ruleVersionNumber()).isEqualTo(1);

    // Evaluated at the future effective date, v2 is now active.
    AssessmentFacts futureFacts = facts(futureDate, nonEuHighEarner.answersByQuestionCode());
    List<RuleEvaluationResult> nowV2 =
        ruleEvaluationService.evaluateRulesForProcedure(
            procedureTargetCode, futureFacts, futureDate);
    assertThat(nowV2.get(0).ruleVersionNumber()).isEqualTo(2);
  }

  @Test
  void ruleEvaluationEndpoint_ownAssessment_returnsBundle_anotherUsersAssessment_isNotFound()
      throws Exception {
    AppUserPrincipal owner = userWithRole("USER");
    AppUserPrincipal intruder = userWithRole("USER");
    String assessmentId =
        extractId(
            mockMvc
                .perform(post(ASSESSMENTS_BASE).with(user(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn());

    mockMvc
        .perform(get(ASSESSMENTS_BASE + "/" + assessmentId + "/rule-evaluations").with(user(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assessmentId").value(assessmentId));

    mockMvc
        .perform(
            get(ASSESSMENTS_BASE + "/" + assessmentId + "/rule-evaluations").with(user(intruder)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get(ASSESSMENTS_BASE + "/" + assessmentId + "/rule-evaluations"))
        .andExpect(status().isUnauthorized());
  }

  private AssessmentFacts facts(LocalDate evaluationDate, Map<String, Object> answers) {
    return new AssessmentFacts(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "WARSAW_GENERAL_ASSESSMENT",
        1,
        AssessmentStatus.IN_PROGRESS,
        null,
        evaluationDate,
        answers);
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
    return "rule-engine-test-" + UUID.randomUUID() + "@example.com";
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
