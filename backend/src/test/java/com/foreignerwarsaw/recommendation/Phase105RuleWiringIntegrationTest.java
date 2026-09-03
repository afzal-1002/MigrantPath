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
 * Phase 10.5 (Production Rule Wiring) regression coverage for the two patterns this phase actually
 * introduced beyond what {@link RecommendationEngineIntegrationTest} (Phase 7) already exercises:
 *
 * <ol>
 *   <li>Multiple REQUIRED-type rules (APPLICABILITY + REQUIREMENT) targeting the same procedure
 *       combine as an AND-set (ADR-009's documented scope: no OR/alternative-legal-basis modeling
 *       yet) - mirrors the real TEMP_RESIDENCE_WORK_BASE + TEMP_RESIDENCE_WORK_MIN_WAGE shape.
 *   <li>An EXCLUSION rule paired with an APPLICABILITY rule whose own fact is gated behind a
 *       QuestionDependency (only ever visible/answerable when a related goal is selected) turns a
 *       "goal never selected, so the gated fact is always MISSING" case into a clean {@code
 *       NOT_APPLICABLE} instead of a noisy {@code MORE_INFORMATION_REQUIRED} - mirrors the real
 *       TEMP_RESIDENCE_WORK_NOT_WORK_GOAL rule, and specifically the reasoning documented in
 *       docs/legal-content/PRODUCTION_RULE_COVERAGE.md for why it exists.
 * </ol>
 *
 * <p>Also proves the original Phase 10 gap - RecommendationService structurally cannot produce a
 * candidate for a procedure no active Rule targets (brief §60's "recommendation zero-candidate
 * regression") - and, symmetrically, that publishing one immediately fixes it, without any
 * hard-coded candidate list anywhere in the recommendation pipeline.
 *
 * <p>Only synthetic {@code TEST_*} procedure/rule content is created here (brief §54/§116) - the
 * real PESEL/MELDUNEK/EU_RESIDENCE_REGISTRATION/TEMP_RESIDENCE_WORK/TEMP_RESIDENCE_STUDY rules this
 * phase actually authored live in the dev database only (via the real Admin API, never a migration
 * - see docs/legal-content/PRODUCTION_RULE_COVERAGE.md), exactly like every other real
 * procedure/rule this codebase has ever published; they are not, and must never be, seeded here.
 */
class Phase105RuleWiringIntegrationTest extends AbstractIntegrationTest {

  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

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
  void noRuleTargetsTheProcedure_producesZeroCandidates_theOriginalPhase10Gap() throws Exception {
    String procedureCode = createPublishedProcedure("TEST_NO_RULE_PROCEDURE");
    AppUserPrincipal applicant = userWithRole("USER");
    String assessmentId = extractId(startAssessment(applicant));
    answer(applicant, assessmentId, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(applicant, assessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(applicant, assessmentId, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(applicant, assessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(applicant, assessmentId, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    complete(applicant, assessmentId);

    MvcResult analyzed = analyze(applicant, assessmentId, null);
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    for (JsonNode rec : body.get("recommendations")) {
      assertThat(rec.get("procedureCode").asText()).isNotEqualTo(procedureCode);
    }
  }

  @Test
  void applicabilityAndRequirementRulesCombineAsAnAndSet_notAlternatives() throws Exception {
    String procedureCode = createPublishedProcedure("TEST_AND_SET_PROCEDURE");

    // APPLICABILITY: outside the EU/EEA/Swiss free-movement group.
    publishRule(
        procedureCode,
        "APPLICABILITY",
        RuleType.APPLICABILITY,
        """
        {"fact":"IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP","operator":"EQUALS","value":true,"code":"THIRD_COUNTRY"}
        """);
    // REQUIREMENT: a salary above a literal figure (no Threshold needed for this synthetic test).
    publishRule(
        procedureCode,
        "REQUIREMENT",
        RuleType.REQUIREMENT,
        """
        {"fact":"MONTHLY_GROSS_SALARY","operator":"GREATER_THAN_OR_EQUAL","value":5000,"code":"MIN_SALARY"}
        """);

    // A Pakistani citizen (satisfies APPLICABILITY) who never answered salary (REQUIREMENT
    // MISSING) must be MORE_INFORMATION_REQUIRED, not a false PRIMARY_MATCH just because
    // APPLICABILITY alone passed - proves the two rules are ANDed, not evaluated as alternatives.
    AppUserPrincipal noSalaryApplicant = userWithRole("USER");
    String noSalaryAssessmentId = extractId(startAssessment(noSalaryApplicant));
    answer(
        noSalaryApplicant,
        noSalaryAssessmentId,
        "CITIZENSHIP_COUNTRY",
        "{\"referenceCode\":\"PK\"}");
    answer(
        noSalaryApplicant, noSalaryAssessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(
        noSalaryApplicant,
        noSalaryAssessmentId,
        "CURRENT_LEGAL_STATUS",
        "{\"referenceCode\":\"NONE\"}");
    answer(
        noSalaryApplicant, noSalaryAssessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(
        noSalaryApplicant,
        noSalaryAssessmentId,
        "PRIMARY_PURPOSE",
        "{\"selectedOptionCodes\":[\"WORK\"]}");
    answer(noSalaryApplicant, noSalaryAssessmentId, "HAS_JOB_OFFER", "{\"booleanValue\":true}");
    complete(noSalaryApplicant, noSalaryAssessmentId);

    MvcResult analyzed = analyze(noSalaryApplicant, noSalaryAssessmentId, null);
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    assertThat(typeFor(body, procedureCode)).isEqualTo("MORE_INFORMATION_REQUIRED");

    // A second applicant with the same facts PLUS a salary answer meeting the REQUIREMENT becomes
    // PRIMARY_MATCH - both rules independently SATISFIED (a fresh assessment, since an answer
    // cannot be changed after ASSESSMENT_NOT_IN_PROGRESS - AssessmentAnswerService's own rule).
    AppUserPrincipal fullFactsApplicant = userWithRole("USER");
    String fullFactsAssessmentId = extractId(startAssessment(fullFactsApplicant));
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "CITIZENSHIP_COUNTRY",
        "{\"referenceCode\":\"PK\"}");
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "CURRENTLY_IN_POLAND",
        "{\"booleanValue\":true}");
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "CURRENT_LEGAL_STATUS",
        "{\"referenceCode\":\"NONE\"}");
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "DATE_OF_BIRTH",
        "{\"dateValue\":\"1990-01-01\"}");
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "PRIMARY_PURPOSE",
        "{\"selectedOptionCodes\":[\"WORK\"]}");
    answer(fullFactsApplicant, fullFactsAssessmentId, "HAS_JOB_OFFER", "{\"booleanValue\":true}");
    answer(
        fullFactsApplicant,
        fullFactsAssessmentId,
        "MONTHLY_GROSS_SALARY",
        "{\"decimalValue\":6000}");
    complete(fullFactsApplicant, fullFactsAssessmentId);

    MvcResult reanalyzed = analyze(fullFactsApplicant, fullFactsAssessmentId, null);
    JsonNode reBody = objectMapper.readTree(reanalyzed.getResponse().getContentAsString());
    assertThat(typeFor(reBody, procedureCode)).isEqualTo("PRIMARY_MATCH");
  }

  @Test
  void exclusionRuleTurnsAGatedMissingFactIntoCleanNotApplicable_notNoisyMoreInfoRequired()
      throws Exception {
    String procedureCode = createPublishedProcedure("TEST_GOAL_GATED_PROCEDURE");

    // APPLICABILITY references HAS_JOB_OFFER - a fact only ever visible/answerable when
    // PRIMARY_PURPOSE contains WORK or HIGHLY_QUALIFIED_WORK (real QuestionDependency, V38).
    publishRule(
        procedureCode,
        "APPLICABILITY",
        RuleType.APPLICABILITY,
        """
        {"fact":"HAS_JOB_OFFER","operator":"EQUALS","value":true,"code":"JOB_OFFER"}
        """);
    // EXCLUSION: the user affirmatively did not select a work-related goal at all.
    publishRule(
        procedureCode,
        "EXCLUSION",
        RuleType.EXCLUSION,
        """
        {"all":[
          {"fact":"PRIMARY_PURPOSE","operator":"NOT_CONTAINS","value":"WORK","code":"NOT_WORK"},
          {"fact":"PRIMARY_PURPOSE","operator":"NOT_CONTAINS","value":"HIGHLY_QUALIFIED_WORK","code":"NOT_HQ_WORK"}
        ]}
        """);

    // A user who selected only STUDY never gets HAS_JOB_OFFER asked at all (it stays gated/
    // invisible) - without the exclusion this would be MORE_INFORMATION_REQUIRED (a MISSING
    // required fact); with it, the exclusion is SATISFIED and wins, per RECOMMENDATION_POLICY.md's
    // classification table (exclusion check runs before the required-rule MISSING check).
    AppUserPrincipal applicant = userWithRole("USER");
    String assessmentId = extractId(startAssessment(applicant));
    answer(applicant, assessmentId, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(applicant, assessmentId, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(applicant, assessmentId, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(applicant, assessmentId, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(applicant, assessmentId, "PRIMARY_PURPOSE", "{\"selectedOptionCodes\":[\"STUDY\"]}");
    answer(applicant, assessmentId, "CURRENTLY_STUDYING", "{\"booleanValue\":true}");
    complete(applicant, assessmentId);

    MvcResult analyzed = analyze(applicant, assessmentId, null);
    JsonNode body = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    assertThat(typeFor(body, procedureCode)).isEqualTo("NOT_APPLICABLE");
  }

  private String typeFor(JsonNode runBody, String procedureCode) {
    for (JsonNode rec : runBody.get("recommendations")) {
      if (rec.get("procedureCode").asText().equals(procedureCode)) {
        return rec.get("recommendationType").asText();
      }
    }
    throw new AssertionError("No recommendation found for " + procedureCode);
  }

  private void publishRule(
      String procedureCode, String roleSuffix, RuleType ruleType, String conditionTree)
      throws Exception {
    String ruleCode = procedureCode + "_" + roleSuffix;
    Rule rule =
        ruleService.createRule(
            ruleCode,
            "Test " + roleSuffix + " rule for " + procedureCode,
            ruleType,
            RuleTargetType.PROCEDURE,
            procedureCode);
    RuleVersion version =
        ruleVersionService.createDraft(
            rule, conditionTree, "rules.test." + ruleCode, actorEntity(editor));
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

  private void complete(AppUserPrincipal actor, String assessmentId) throws Exception {
    mockMvc
        .perform(
            post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete")
                .with(user(actor))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private MvcResult analyze(AppUserPrincipal actor, String assessmentId, String evaluationDate)
      throws Exception {
    var request =
        post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs")
            .with(user(actor))
            .with(csrf());
    if (evaluationDate != null) {
      request = request.param("evaluationDate", evaluationDate);
    }
    return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
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
    return "phase105-rule-wiring-test-" + UUID.randomUUID() + "@example.com";
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
