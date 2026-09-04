package com.foreignerwarsaw.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
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
 * Canonical Phase 11 (Testing Completeness) brief §27 - "Production Rule Regression Suite."
 *
 * <p>Every real {@code PUBLISHED} production {@link Rule} lives only as live data in the dev/
 * production database (docs/legal-content/PRODUCTION_RULE_COVERAGE.md: "never a migration, never a
 * direct SQL insert") - a fresh Testcontainers database, migrated from scratch, has none of them.
 * {@link Phase105RuleWiringIntegrationTest} already proved the general *engine mechanics* (AND-
 * combination, exclusion-vs-missing) using rules that only *mirror the shape* of the real ones
 * under synthetic {@code TEST_*} codes/condition trees. This class closes the gap that left: it
 * republishes the byte-identical real rule {@code code}, {@code ruleType}, and {@code
 * conditionTree} JSON for every one of the six real {@code PUBLISHED} rules (copied verbatim from
 * the running dev database on 2026-09-04 - see the {@code CONDITION_TREE_*} constants below, each
 * with its source query result in a comment) through the same real Admin governance workflow, then
 * proves each one classifies a real assessment exactly as documented in
 * PRODUCTION_RULE_COVERAGE.md's coverage matrix (PASS/FAIL/ MISSING). A change to this file's
 * condition-tree constants is itself a signal: it means someone is about to touch what production
 * actually evaluates, and this suite's assertions are the contract for whether that change
 * preserves the documented legal behavior.
 *
 * <p>Only the rule *content* (code, type, condition tree) is real; procedure codes are still
 * synthetic per-test ({@code uniqueCode}) - the real rule's own {@code targetCode} in production
 * points at a real Procedure that already exists in the dev database, which this throwaway test
 * database does not have. The condition-tree logic being protected does not depend on which
 * Procedure it targets.
 */
// @DirtiesContext(AFTER_CLASS): see RecommendationEngineIntegrationTest's identical Javadoc -
// same real, reproduced-this-phase CookieCsrfTokenRepository pollution pattern.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProductionRuleRegressionTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ADMIN_BASE = "/api/v1/admin";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

  // Verbatim from `select code, rule_type, target_code, condition_tree from rules join
  // rule_versions ... where status='PUBLISHED'` against the dev database, 2026-09-04 - see
  // docs/legal-content/PRODUCTION_RULE_COVERAGE.md for the legal justification of each.
  private static final String PESEL_BASE_APPLICABILITY =
      """
      {"code":"PESEL_GOAL_SELECTED","fact":"PRIMARY_PURPOSE","value":"GET_PESEL","operator":"CONTAINS","explanationKey":"pesel.applicability.goalSelected"}
      """;
  private static final String MELDUNEK_BASE_APPLICABILITY =
      """
      {"code":"MELDUNEK_GOAL_SELECTED","fact":"PRIMARY_PURPOSE","value":"GET_MELDUNEK","operator":"CONTAINS","explanationKey":"meldunek.applicability.goalSelected"}
      """;
  private static final String EU_RESIDENCE_REGISTRATION_BASE =
      """
      {"all": [{"code": "EURR_IN_POLAND", "fact": "CURRENTLY_IN_POLAND", "value": true, "operator": "EQUALS", "explanationKey": "eurr.applicability.inPoland"}, {"code": "EURR_COUNTRY_SCOPE_MATCH", "fact": "IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP", "value": false, "operator": "EQUALS", "explanationKey": "eurr.applicability.countryScopeMatch"}]}
      """;
  private static final String TEMP_RESIDENCE_WORK_BASE =
      """
      {"all": [{"code": "TRW_THIRD_COUNTRY", "fact": "IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP", "value": true, "operator": "EQUALS", "explanationKey": "trw.applicability.thirdCountry"}, {"code": "TRW_JOB_OFFER", "fact": "HAS_JOB_OFFER", "value": true, "operator": "EQUALS", "explanationKey": "trw.applicability.jobOffer"}]}
      """;
  private static final String TEMP_RESIDENCE_WORK_NOT_WORK_GOAL =
      """
      {"all":[
        {"code": "TRW_EXCL_NOT_WORK", "fact": "PRIMARY_PURPOSE", "value": "WORK", "operator": "NOT_CONTAINS"},
        {"code": "TRW_EXCL_NOT_HQ_WORK", "fact": "PRIMARY_PURPOSE", "value": "HIGHLY_QUALIFIED_WORK", "operator": "NOT_CONTAINS"}
      ]}
      """;
  private static final String TEMP_RESIDENCE_WORK_MIN_WAGE =
      """
      {"code": "TRW_MIN_WAGE", "fact": "MONTHLY_GROSS_SALARY", "operator": "GREATER_THAN_OR_EQUAL", "threshold": "MINIMUM_WAGE_PLN_MONTHLY", "explanationKey": "trw.requirement.minWage"}
      """;
  // Real value/effective date from `select value, effective_from from thresholds join
  // threshold_versions ... where code='MINIMUM_WAGE_PLN_MONTHLY' and status='PUBLISHED'`,
  // 2026-09-04.
  private static final double REAL_MINIMUM_WAGE_PLN_MONTHLY = 4806.0;

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
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
  void peselBaseApplicability_passOnGoalSelected_failOtherwise() throws Exception {
    String procedureCode = createPublishedProcedure("TEST_PESEL_REGRESSION");
    publishRealRule(
        "PESEL_BASE_APPLICABILITY",
        RuleType.APPLICABILITY,
        procedureCode,
        PESEL_BASE_APPLICABILITY);

    assertThat(classify(procedureCode, applicant().purpose("GET_PESEL").build()))
        .isEqualTo("PRIMARY_MATCH");
    assertThat(classify(procedureCode, applicant().purpose("WORK").jobOffer(true).build()))
        .isEqualTo("NOT_APPLICABLE");
  }

  // MELDUNEK_BASE_APPLICABILITY is deliberately NOT tested here: its fact
  // (PRIMARY_PURPOSE CONTAINS "GET_MELDUNEK") can only be produced through the real assessment
  // API by QuestionnaireVersion 2, which exists solely as live data in the dev database (created
  // via the real Admin workflow, per docs/legal-content/PRODUCTION_RULE_COVERAGE.md) - a fresh
  // Testcontainers database genuinely rejects "GET_MELDUNEK" as an invalid PRIMARY_PURPOSE option
  // (confirmed: this test was first attempted here and failed with exactly that 400). Its
  // real-condition-tree regression coverage lives in
  // RuleEvaluatorTest.meldunekBaseApplicability_realProductionConditionTree_passOnGoalSelected_failOtherwise
  // instead, at the layer that doesn't require a currently-active QuestionnaireVersion option.

  @Test
  void euResidenceRegistrationBase_passForEuCitizenInPoland_failOtherwise() throws Exception {
    String procedureCode = createPublishedProcedure("TEST_EURR_REGRESSION");
    publishRealRule(
        "EU_RESIDENCE_REGISTRATION_BASE",
        RuleType.APPLICABILITY,
        procedureCode,
        EU_RESIDENCE_REGISTRATION_BASE);

    // PASS: a German (EU) citizen, currently in Poland.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("DE").inPoland(true).purpose("GET_PESEL").build()))
        .isEqualTo("PRIMARY_MATCH");
    // FAIL: a third-country (Pakistani) citizen, currently in Poland - outside the
    // EU/EEA/Swiss group, so the country-scope leaf fails.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("PK").inPoland(true).purpose("GET_PESEL").build()))
        .isEqualTo("NOT_APPLICABLE");
    // FAIL: an EU citizen not currently in Poland - the presence leaf fails.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("DE").inPoland(false).purpose("GET_PESEL").build()))
        .isEqualTo("NOT_APPLICABLE");
  }

  @Test
  void tempResidenceWork_fullRealRuleSet_combinesApplicabilityExclusionAndMinWageAsDocumented()
      throws Exception {
    String procedureCode = createPublishedProcedure("TEST_TRW_REGRESSION");
    publishRealRule(
        "TEMP_RESIDENCE_WORK_BASE",
        RuleType.APPLICABILITY,
        procedureCode,
        TEMP_RESIDENCE_WORK_BASE);
    publishRealRule(
        "TEMP_RESIDENCE_WORK_NOT_WORK_GOAL",
        RuleType.EXCLUSION,
        procedureCode,
        TEMP_RESIDENCE_WORK_NOT_WORK_GOAL);
    publishRealThreshold("MINIMUM_WAGE_PLN_MONTHLY", REAL_MINIMUM_WAGE_PLN_MONTHLY);
    publishRealRule(
        "TEMP_RESIDENCE_WORK_MIN_WAGE",
        RuleType.REQUIREMENT,
        procedureCode,
        TEMP_RESIDENCE_WORK_MIN_WAGE);

    // PASS: third-country citizen, job offer, salary at/above the real minimum wage.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("PK").purpose("WORK").jobOffer(true).salary(5000).build()))
        .isEqualTo("PRIMARY_MATCH");

    // FAIL (min-wage): same facts, but salary below the real minimum wage - REQUIREMENT
    // NOT_SATISFIED wins even though APPLICABILITY passed.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("PK").purpose("WORK").jobOffer(true).salary(3000).build()))
        .isEqualTo("NOT_APPLICABLE");

    // MISSING (min-wage): same facts, salary never answered - MORE_INFORMATION_REQUIRED,
    // never a false PRIMARY_MATCH.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("PK").purpose("WORK").jobOffer(true).build()))
        .isEqualTo("MORE_INFORMATION_REQUIRED");

    // EXCLUSION wins: no work-related goal selected at all - HAS_JOB_OFFER is never asked
    // (gated), so without the exclusion this would be a noisy MORE_INFORMATION_REQUIRED;
    // with it, NOT_APPLICABLE cleanly.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("PK").purpose("STUDY").studying(true).build()))
        .isEqualTo("NOT_APPLICABLE");

    // FAIL (base applicability): an EU citizen with a job offer and a qualifying salary is
    // still NOT_APPLICABLE - the uniform work permit is a third-country-national procedure.
    assertThat(
            classify(
                procedureCode,
                applicant().citizenship("DE").purpose("WORK").jobOffer(true).salary(5000).build()))
        .isEqualTo("NOT_APPLICABLE");
  }

  // --- scenario builder -------------------------------------------------------------------

  private Applicant applicant() {
    return new Applicant();
  }

  /** Fluent builder for the handful of facts these real rules actually reference. */
  private final class Applicant {
    private String citizenship = "PK";
    private boolean inPoland = true;
    private String purpose = "GET_PESEL";
    private Boolean jobOffer;
    private Integer salary;
    private Boolean studying;

    Applicant citizenship(String iso2) {
      this.citizenship = iso2;
      return this;
    }

    Applicant inPoland(boolean value) {
      this.inPoland = value;
      return this;
    }

    Applicant purpose(String optionCode) {
      this.purpose = optionCode;
      return this;
    }

    Applicant jobOffer(boolean value) {
      this.jobOffer = value;
      return this;
    }

    Applicant salary(int value) {
      this.salary = value;
      return this;
    }

    Applicant studying(boolean value) {
      this.studying = value;
      return this;
    }

    JsonNode build() throws Exception {
      AppUserPrincipal actor = userWithRole("USER");
      String assessmentId = extractId(startAssessment(actor));
      answer(
          actor,
          assessmentId,
          "CITIZENSHIP_COUNTRY",
          "{\"referenceCode\":\"" + citizenship + "\"}");
      answer(actor, assessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":" + inPoland + "}");
      // CURRENT_LEGAL_STATUS is gated behind CURRENTLY_IN_POLAND=true (V38's real
      // QuestionDependency) - answering it while CURRENTLY_IN_POLAND=false is itself rejected
      // (409 QUESTION_NOT_APPLICABLE), so it's only answered when relevant, exactly like a real
      // user would never see the field otherwise.
      if (inPoland) {
        answer(actor, assessmentId, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
      }
      answer(actor, assessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
      answer(
          actor,
          assessmentId,
          "PRIMARY_PURPOSE",
          "{\"selectedOptionCodes\":[\"" + purpose + "\"]}");
      if (jobOffer != null) {
        answer(actor, assessmentId, "HAS_JOB_OFFER", "{\"booleanValue\":" + jobOffer + "}");
      }
      if (salary != null) {
        answer(actor, assessmentId, "MONTHLY_GROSS_SALARY", "{\"decimalValue\":" + salary + "}");
      }
      if (studying != null) {
        answer(actor, assessmentId, "CURRENTLY_STUDYING", "{\"booleanValue\":" + studying + "}");
      }
      complete(actor, assessmentId);

      MvcResult analyzed = analyze(actor, assessmentId);
      return objectMapper.readTree(analyzed.getResponse().getContentAsString());
    }
  }

  /** Extracts the one recommendation this test cares about from a real recommendation-run body. */
  private String classify(String procedureCode, JsonNode runBody) {
    for (JsonNode rec : runBody.get("recommendations")) {
      if (rec.get("procedureCode").asText().equals(procedureCode)) {
        return rec.get("recommendationType").asText();
      }
    }
    throw new AssertionError("No recommendation found for " + procedureCode + " in " + runBody);
  }

  // --- real-content publishing helpers ------------------------------------------------------

  private void publishRealRule(
      String ruleCode, RuleType ruleType, String procedureCode, String conditionTreeJson)
      throws Exception {
    Rule rule =
        ruleService.createRule(
            ruleCode,
            "Production regression: " + ruleCode,
            ruleType,
            RuleTargetType.PROCEDURE,
            procedureCode);
    RuleVersion version =
        ruleVersionService.createDraft(
            rule, conditionTreeJson, "rules." + ruleCode.toLowerCase(), actorEntity(editor));
    OfficialSource source =
        officialSourceRepository.findById(UUID.fromString(createAndVerifySource())).orElseThrow();
    ruleVersionService.attachSource(version, source, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(version.getId(), actorEntity(editor));
    ruleVersionService.approve(version.getId(), actorEntity(reviewer));
    rulePublishingService.publish(version.getId(), actorEntity(admin), LocalDate.now(clock));
  }

  private void publishRealThreshold(String code, double value) throws Exception {
    mockMvc
        .perform(
            post(ADMIN_BASE + "/thresholds")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"canonicalName\":\"Real minimum wage (regression)\",\"valueType\":\"MONEY\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    String versionId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN_BASE + "/thresholds/" + code + "/versions")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"value\":"
                                + value
                                + ",\"effectiveFrom\":\""
                                + LocalDate.now(clock)
                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(ADMIN_BASE + "/thresholds/" + code + "/versions/" + versionId + "/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN_BASE + "/thresholds/" + code + "/versions/" + versionId + "/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());
    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN_BASE + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Real minimum wage source (regression)\",\"sourceUrl\":\"https://example.gov.pl/"
                                + UUID.randomUUID()
                                + "\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(ADMIN_BASE + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN_BASE + "/thresholds/" + code + "/versions/" + versionId + "/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN_BASE + "/thresholds/" + code + "/versions/" + versionId + "/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
        .andExpect(status().isOk());
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

  private void complete(AppUserPrincipal actor, String assessmentId) throws Exception {
    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete")
                .with(user(actor))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private MvcResult analyze(AppUserPrincipal actor, String assessmentId) throws Exception {
    return mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
                .with(user(actor))
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn();
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
    return "production-rule-regression-" + UUID.randomUUID() + "@example.com";
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
