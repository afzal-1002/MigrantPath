package com.foreignerwarsaw.user.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.admin.review.AdminReview;
import com.foreignerwarsaw.admin.review.AdminReviewRepository;
import com.foreignerwarsaw.common.audit.AuditLogRepository;
import com.foreignerwarsaw.procedure.core.ProcedureRepository;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentRepository;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleTargetType;
import com.foreignerwarsaw.rules.core.RuleType;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionService;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) - personal-data export and self-service account
 * deletion, end to end against the real HTTP/Security/PostgreSQL stack (brief §64-§66/§143-§147).
 * Reuses {@code AuthIntegrationTest}'s real register→verify→login pattern (needs a genuine password
 * for the deletion reauthentication check, unlike this codebase's synthetic {@code userWithRole}
 * helper). Only synthetic {@code TEST_*} procedure/rule content is ever created.
 *
 * <p>Every actor in this class - applicant, CONTENT_EDITOR, LEGAL_REVIEWER, ADMIN alike - goes
 * through the real register→verify→login cookie flow, never the {@code
 * SecurityMockMvcRequestPostProcessors.with(user(...))}/{@code with(csrf())} shortcut other admin
 * integration tests use. A real finding this phase: mixing the two styles within one test class
 * (real cookie-based CSRF for some calls, the test-support CSRF bypass for others) corrupts the
 * shared {@code CookieCsrfTokenRepository} state for whichever real-cookie call comes next, even
 * within the very same test method (not just across classes, which is the narrower case {@code
 * AdminGovernanceIntegrationTest}'s own Javadoc already documents and works around with
 * {@code @DirtiesContext}). Staying on one consistent style end to end avoids it entirely, and is
 * arguably the more faithful test anyway - it proves the full HTTP+session flow for governance
 * actors too, not just for the end-user applicant.
 */
class AccountPrivacyIntegrationTest extends AbstractIntegrationTest {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");
  private static final String PASSWORD = "correct-horse-battery";
  private static final String CONTENT_BASE = "/api/v1/internal/content";
  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private AssessmentRepository assessmentRepository;
  @Autowired private UserCaseRepository userCaseRepository;
  @Autowired private ProcedureRepository procedureRepository;
  @Autowired private RuleService ruleService;
  @Autowired private RuleVersionService ruleVersionService;
  @Autowired private RulePublishingService rulePublishingService;
  @Autowired private OfficialSourceRepository officialSourceRepository;
  @Autowired private AdminReviewRepository adminReviewRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private Clock clock;

  // --- shared HTTP plumbing (mirrors AuthIntegrationTest) ---

  private Cookie obtainCsrfCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/platform/status")).andReturn();
    Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return csrf;
  }

  private MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder builder, Cookie csrf) {
    return builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
  }

  private String registerJson(String email, String password) {
    return """
        {"email":"%s","password":"%s","firstName":"Pat","acceptTerms":true,"acceptPrivacyPolicy":true}
        """
        .formatted(email, password);
  }

  private String extractTokenFromEmail(String html) {
    Matcher matcher = TOKEN_PATTERN.matcher(html);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private void registerAndVerify(String email, String password) throws Exception {
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, password)))
        .andExpect(status().isCreated());
    String html = findLatestMessageTo(email);
    String token = extractTokenFromEmail(html);
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/verify-email"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk());
  }

  private Cookie loginAndGetSessionCookie(String email, String password, Cookie csrf)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/login"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    return sessionCookie;
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }

  // --- a governance actor: real register→verify→login, role granted directly (no self-service
  // HTTP endpoint exists to become CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN - the same shortcut every
  // other integration test's synthetic userWithRole() takes, just with a real password/session
  // behind it here instead of a fake hash). ---

  private record Actor(Cookie csrf, Cookie session, User user) {}

  private Actor registerVerifyLoginAndGrantRole(String prefix, String roleCode) throws Exception {
    String email = uniqueEmail(prefix);
    registerAndVerify(email, PASSWORD);
    User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
    Role role = roleRepository.findByCode(roleCode).orElseThrow();
    u.addRole(role);
    u = userRepository.save(u);
    Cookie csrf = obtainCsrfCookie();
    Cookie session = loginAndGetSessionCookie(email, PASSWORD, csrf);
    return new Actor(csrf, session, u);
  }

  // --- rich personal-data fixture: a published procedure/rule, an assessment, a case ---

  private String buildCaseForUser(String email, String password, Cookie csrf, Cookie session)
      throws Exception {
    String procedureCode = uniqueCode("TEST_PRIVACY_PROCEDURE");
    createPublishedProcedure(procedureCode);
    publishRule(procedureCode);

    MvcResult started =
        mockMvc
            .perform(withCsrf(post(ASSESSMENTS_BASE), csrf).cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    String assessmentId = extractId(started);
    answer(assessmentId, csrf, session, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(assessmentId, csrf, session, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(assessmentId, csrf, session, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(assessmentId, csrf, session, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(
        assessmentId,
        csrf,
        session,
        "PRIMARY_PURPOSE",
        "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    mockMvc
        .perform(
            withCsrf(post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete"), csrf)
                .cookie(session))
        .andExpect(status().isOk());

    MvcResult analyzed =
        mockMvc
            .perform(
                withCsrf(post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs"), csrf)
                    .cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode runBody = objectMapper.readTree(analyzed.getResponse().getContentAsString());
    String recommendationId = null;
    for (JsonNode rec : runBody.get("recommendations")) {
      if (rec.get("procedureCode").asText().equals(procedureCode)) {
        recommendationId = rec.get("id").asText();
      }
    }
    assertThat(recommendationId).isNotNull();

    MvcResult created =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/recommendations/" + recommendationId + "/cases"), csrf)
                    .cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  // --- export ---

  @Test
  void export_isUnauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/account/export")).andExpect(status().isUnauthorized());
  }

  @Test
  void export_includesRealPersonalDataAndExcludesSecrets_neverLeaksAnotherUser() throws Exception {
    String emailA = uniqueEmail("export-a");
    String emailB = uniqueEmail("export-b");
    registerAndVerify(emailA, PASSWORD);
    registerAndVerify(emailB, PASSWORD);
    Cookie csrfA = obtainCsrfCookie();
    Cookie sessionA = loginAndGetSessionCookie(emailA, PASSWORD, csrfA);
    Cookie csrfB = obtainCsrfCookie();
    Cookie sessionB = loginAndGetSessionCookie(emailB, PASSWORD, csrfB);

    String caseIdA = buildCaseForUser(emailA, PASSWORD, csrfA, sessionA);
    String caseIdB = buildCaseForUser(emailB, PASSWORD, csrfB, sessionB);

    MvcResult exportResult =
        mockMvc
            .perform(get("/api/v1/account/export").cookie(sessionA))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(jsonPath("$.exportSchemaVersion").value(1))
            .andExpect(jsonPath("$.account.email").value(emailA))
            .andExpect(jsonPath("$.consents[?(@.consentType == 'TERMS_OF_SERVICE')]").exists())
            .andExpect(jsonPath("$.consents[?(@.consentType == 'PRIVACY_POLICY')]").exists())
            .andExpect(jsonPath("$.assessments.length()").value(1))
            .andExpect(
                jsonPath("$.assessments[0].answers[?(@.questionCode == 'CITIZENSHIP_COUNTRY')]")
                    .exists())
            .andExpect(jsonPath("$.recommendationRuns.length()").value(1))
            .andExpect(jsonPath("$.cases[0].id").value(caseIdA))
            .andReturn();

    String raw = exportResult.getResponse().getContentAsString();
    // Explicit-DTO structure already makes these structurally absent; this is a cheap,
    // additional regression pin (brief §196), not the sole guarantee.
    assertThat(raw).doesNotContainIgnoringCase("passwordHash");
    assertThat(raw).doesNotContainIgnoringCase("sessionId");
    assertThat(raw).doesNotContain("SESSION=");
    assertThat(raw).doesNotContainIgnoringCase("tokenHash");
    assertThat(raw).doesNotContainIgnoringCase("csrf");
    // User A's export never mentions User B's case at all.
    assertThat(raw).doesNotContain(caseIdB);
    assertThat(raw).doesNotContain(emailB);
  }

  // --- deletion ---

  @Test
  void delete_wrongPassword_isRejectedAndAccountUntouched() throws Exception {
    String email = uniqueEmail("delete-wrongpw");
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie session = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(
            withCsrf(post("/api/v1/account/delete"), csrf)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"confirmation\":\"DELETE\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("ACCOUNT_REAUTHENTICATION_FAILED"));

    assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
  }

  @Test
  void delete_missingConfirmation_isRejected() throws Exception {
    String email = uniqueEmail("delete-noconfirm");
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie session = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(
            withCsrf(post("/api/v1/account/delete"), csrf)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"confirmation\":\"nope\"}".formatted(PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_CONFIRMATION_REQUIRED"));

    assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
  }

  /**
   * The single largest privacy-lifecycle proof (brief §65/§66/§145/§146): correct-password deletion
   * removes the whole personal graph, invalidates every session (not just the one that requested
   * deletion), leaves a completely unrelated user's data untouched, and the same email can safely
   * register again as a brand-new, unconnected identity.
   */
  @Test
  void
      delete_correctPassword_removesPersonalData_invalidatesAllSessions_reRegistrationIsANewIdentity()
          throws Exception {
    String targetEmail = uniqueEmail("delete-target");
    String bystanderEmail = uniqueEmail("delete-bystander");
    registerAndVerify(targetEmail, PASSWORD);
    registerAndVerify(bystanderEmail, PASSWORD);

    Cookie csrfTarget1 = obtainCsrfCookie();
    Cookie sessionTarget1 = loginAndGetSessionCookie(targetEmail, PASSWORD, csrfTarget1);
    // A second, independent session for the same account (brief §12/§103 - "Browser A / Browser
    // B").
    Cookie csrfTarget2 = obtainCsrfCookie();
    Cookie sessionTarget2 = loginAndGetSessionCookie(targetEmail, PASSWORD, csrfTarget2);

    Cookie csrfBystander = obtainCsrfCookie();
    Cookie sessionBystander = loginAndGetSessionCookie(bystanderEmail, PASSWORD, csrfBystander);

    String targetCaseId = buildCaseForUser(targetEmail, PASSWORD, csrfTarget1, sessionTarget1);
    String bystanderCaseId =
        buildCaseForUser(bystanderEmail, PASSWORD, csrfBystander, sessionBystander);

    UUID targetUserId = userRepository.findByEmailIgnoreCase(targetEmail).orElseThrow().getId();
    assertThat(assessmentRepository.findByUser_IdOrderByStartedAtDesc(targetUserId)).hasSize(1);
    assertThat(userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(targetUserId)).hasSize(1);

    mockMvc
        .perform(
            withCsrf(post("/api/v1/account/delete"), csrfTarget1)
                .cookie(sessionTarget1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"confirmation\":\"DELETE\"}".formatted(PASSWORD)))
        .andExpect(status().isNoContent());

    // Both sessions for the deleted account are now unauthorized - including the second one,
    // which never itself called delete (brief §12/§31/§103, mandatory).
    mockMvc
        .perform(get("/api/v1/users/me").cookie(sessionTarget1))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/users/me").cookie(sessionTarget2))
        .andExpect(status().isUnauthorized());

    // Login with the old credentials now fails outright - the account is genuinely gone.
    Cookie csrfRetry = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrfRetry)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(targetEmail, PASSWORD)))
        .andExpect(status().isUnauthorized());

    // The whole personal-data graph is gone, at the database level.
    assertThat(userRepository.findByEmailIgnoreCase(targetEmail)).isEmpty();
    assertThat(userRepository.findById(targetUserId)).isEmpty();
    assertThat(assessmentRepository.findByUser_IdOrderByStartedAtDesc(targetUserId)).isEmpty();
    assertThat(userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(targetUserId)).isEmpty();

    // The completely unrelated bystander account is entirely untouched.
    UUID bystanderUserId =
        userRepository.findByEmailIgnoreCase(bystanderEmail).orElseThrow().getId();
    assertThat(userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(bystanderUserId)).hasSize(1);
    mockMvc
        .perform(get("/api/v1/cases/" + bystanderCaseId).cookie(sessionBystander))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(bystanderCaseId));
    assertThat(targetCaseId).isNotEqualTo(bystanderCaseId);

    // Re-registering the same email creates a brand-new, unconnected identity (brief §13/§183) -
    // never reattached to the deleted account's old history.
    registerAndVerify(targetEmail, "a-different-password-1");
    UUID newUserId = userRepository.findByEmailIgnoreCase(targetEmail).orElseThrow().getId();
    assertThat(newUserId).isNotEqualTo(targetUserId);
    assertThat(assessmentRepository.findByUser_IdOrderByStartedAtDesc(newUserId)).isEmpty();
    assertThat(userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(newUserId)).isEmpty();
  }

  /**
   * Canonical Phase 12's headline governance fix (brief §66/§147, V48 migration): a CONTENT_EDITOR
   * who has submitted real content for review can still delete their own account through the
   * ordinary self-service flow - the review, the published content, and the audit trail all
   * survive; only the live account/email link is gone.
   */
  @Test
  void staffAccountDeletion_preservesReviewAndPublishedContentAndAuditHistory() throws Exception {
    Actor editor = registerVerifyLoginAndGrantRole("staff-delete-editor", "CONTENT_EDITOR");
    UUID editorId = editor.user().getId();

    String procedureCode = uniqueCode("TEST_STAFF_DELETION_PROCEDURE");
    mockMvc
        .perform(
            withCsrf(post(CONTENT_BASE + "/procedures"), editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Test\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(procedureCode)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + procedureCode + "/versions"),
                    editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test v1\",\"summary\":\"s\",\"description\":\"d\"}"))
        .andExpect(status().isCreated());
    String sourceId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        withCsrf(post(CONTENT_BASE + "/sources"), editor.csrf())
                            .cookie(editor.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"title\":\"s\",\"sourceUrl\":\"https://example.gov.pl/"
                                    + UUID.randomUUID()
                                    + "\",\"sourceType\":\"LEGISLATION\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + procedureCode + "/versions/1/sources"),
                    editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());
    // The real AdminReview-creating submit path is /api/v1/admin/procedures/**/submit, not the
    // legacy /api/v1/internal/content/** one (which only flips the domain status -
    // ProcedureVersionService#submitForReview never calls ContentReviewCoordinator itself; only
    // AdminProcedureController's submit action does, see that class and
    // AdminGovernanceIntegrationTest's
    // own real-flow pattern).
    mockMvc
        .perform(
            withCsrf(
                    post("/api/v1/admin/procedures/" + procedureCode + "/versions/1/submit"),
                    editor.csrf())
                .cookie(editor.session()))
        .andExpect(status().isOk());

    // This test is the only one in this class that creates a PENDING admin_review row, so the
    // most-recently-created PENDING review (list is ascending by createdAt) is unambiguously this
    // one - avoids a separate, lazy-loading-risky lookup through ProcedureVersion.
    List<AdminReview> pending =
        adminReviewRepository.findByStatusOrderByCreatedAtAsc(
            com.foreignerwarsaw.admin.review.AdminReviewStatus.PENDING);
    assertThat(pending).isNotEmpty();
    AdminReview review = pending.get(pending.size() - 1);
    UUID reviewId = review.getId();
    assertThat(review.getSubmittedByActorRef()).isEqualTo(editorId);
    long auditRowsBefore = auditLogRepository.count();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/account/delete"), editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"confirmation\":\"DELETE\"}".formatted(PASSWORD)))
        .andExpect(status().isNoContent());

    assertThat(userRepository.findById(editorId)).isEmpty();

    AdminReview reviewAfter = adminReviewRepository.findById(reviewId).orElseThrow();
    assertThat(reviewAfter.getSubmittedBy()).isNull();
    assertThat(reviewAfter.getSubmittedByActorRef()).isEqualTo(editorId);
    assertThat(procedureRepository.findByCodeIgnoreCase(procedureCode)).isPresent();
    assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(auditRowsBefore);
  }

  // --- helpers reused from the same real Admin-workflow pattern as other integration tests ---

  private void createPublishedProcedure(String code) throws Exception {
    Actor editor = registerVerifyLoginAndGrantRole("proc-editor", "CONTENT_EDITOR");
    Actor reviewer = registerVerifyLoginAndGrantRole("proc-reviewer", "LEGAL_REVIEWER");
    Actor admin = registerVerifyLoginAndGrantRole("proc-admin", "ADMIN");

    mockMvc
        .perform(
            withCsrf(post(CONTENT_BASE + "/procedures"), editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Test procedure\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            withCsrf(post(CONTENT_BASE + "/procedures/" + code + "/versions"), editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test v1\",\"summary\":\"s\",\"description\":\"d\"}"))
        .andExpect(status().isCreated());
    // At least one step is required for CaseCreationValidator to consider this procedure
    // case-ready (CASE_CONTENT_NOT_READY otherwise) - a real finding building this fixture.
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + code + "/versions/1/steps"), editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"stableCode\":\"TEST_STEP_1\",\"title\":\"Prepare documents\",\"description\":\"Gather everything\",\"stepType\":\"PREPARATION\",\"sortOrder\":1,\"mandatory\":true}"))
        .andExpect(status().isCreated());
    String sourceId = createAndVerifySource(editor, reviewer);
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + code + "/versions/1/sources"),
                    editor.csrf())
                .cookie(editor.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + code + "/versions/1/submit"),
                    editor.csrf())
                .cookie(editor.session()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + code + "/versions/1/approve"),
                    reviewer.csrf())
                .cookie(reviewer.session()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withCsrf(
                    post(CONTENT_BASE + "/procedures/" + code + "/versions/1/publish"),
                    admin.csrf())
                .cookie(admin.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
        .andExpect(status().isOk());

    this.lastEditor = editor.user();
    this.lastReviewer = reviewer.user();
    this.lastAdmin = admin.user();
  }

  private User lastEditor;
  private User lastReviewer;
  private User lastAdmin;

  private void publishRule(String procedureCode) throws Exception {
    Actor editor = registerVerifyLoginAndGrantRole("rule-editor", "CONTENT_EDITOR");
    Actor reviewer = registerVerifyLoginAndGrantRole("rule-reviewer", "LEGAL_REVIEWER");

    Rule rule =
        ruleService.createRule(
            procedureCode + "_RULE",
            "Test rule for " + procedureCode,
            RuleType.APPLICABILITY,
            RuleTargetType.PROCEDURE,
            procedureCode);
    RuleVersion version =
        ruleVersionService.createDraft(
            rule,
            "{\"fact\":\"PRIMARY_PURPOSE\",\"operator\":\"CONTAINS\",\"value\":\"GET_PESEL\"}",
            "rules.test." + procedureCode,
            editor.user());
    OfficialSource source =
        officialSourceRepository
            .findById(UUID.fromString(createAndVerifySource(editor, reviewer)))
            .orElseThrow();
    ruleVersionService.attachSource(version, source, SourceRole.PRIMARY);
    ruleVersionService.submitForReview(version.getId(), editor.user());
    ruleVersionService.approve(version.getId(), reviewer.user());
    rulePublishingService.publish(version.getId(), lastAdmin, LocalDate.now(clock));
  }

  private String createAndVerifySource(Actor editor, Actor reviewer) throws Exception {
    String sourceId =
        extractId(
            mockMvc
                .perform(
                    withCsrf(post(CONTENT_BASE + "/sources"), editor.csrf())
                        .cookie(editor.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Test source\",\"sourceUrl\":\"https://example.gov.pl/"
                                + UUID.randomUUID()
                                + "\",\"sourceType\":\"LEGISLATION\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            withCsrf(post(CONTENT_BASE + "/sources/" + sourceId + "/verify"), reviewer.csrf())
                .cookie(reviewer.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\",\"notes\":\"test\"}"))
        .andExpect(status().isOk());
    return sourceId;
  }

  private void answer(
      String assessmentId, Cookie csrf, Cookie session, String questionCode, String bodyJson)
      throws Exception {
    mockMvc
        .perform(
            withCsrf(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        ASSESSMENTS_BASE + "/" + assessmentId + "/answers/" + questionCode),
                    csrf)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
        .andExpect(status().isOk());
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
