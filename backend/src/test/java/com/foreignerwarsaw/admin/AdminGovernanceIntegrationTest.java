package com.foreignerwarsaw.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.AppUserPrincipal;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 9's real end-to-end admin-governance coverage (brief §116-§127), against the real HTTP +
 * Spring Security + PostgreSQL 18 stack, synthetic {@code TEST_*} content only. Reuses {@code
 * ProcedureVersioningIntegrationTest}'s editor/reviewer/admin actor pattern exactly.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: found, empirically, that running this class's many
 * role-switching {@code with(user(...))}/{@code with(csrf())} MockMvc requests before {@code
 * AuthIntegrationTest} in the same cached Spring context left the shared {@code
 * CookieCsrfTokenRepository}/security filter chain state such that {@code AuthIntegrationTest}'s
 * very first request stopped receiving an {@code XSRF-TOKEN} cookie - reproduced deterministically
 * running just these two classes together, absent when either runs alone. Forcing a fresh context
 * after this class isolates whatever singleton state it perturbs from bleeding into later test
 * classes, at the cost of one extra context rebuild - the standard fix for this class of
 * cached-context test pollution, without weakening this class's own real HTTP+Security coverage.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminGovernanceIntegrationTest extends AbstractIntegrationTest {

  private static final String ADMIN = "/api/v1/admin";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private Clock clock;

  private AppUserPrincipal editor;
  private AppUserPrincipal reviewer;
  private AppUserPrincipal admin;
  private AppUserPrincipal plainUser;
  private AppUserPrincipal editorAndReviewer;

  @BeforeEach
  void setUpActors() {
    editor = userWithRole("CONTENT_EDITOR");
    reviewer = userWithRole("LEGAL_REVIEWER");
    admin = userWithRole("ADMIN");
    plainUser = userWithRole("USER");
    // Holds both roles so it can legitimately reach /submit (CONTENT_EDITOR) and /approve
    // (LEGAL_REVIEWER) alike - the only way to isolate the self-approval check itself (brief
    // §5/§117) from the separate, coarser role-gate a plain CONTENT_EDITOR would hit first.
    editorAndReviewer = userWithRoles("CONTENT_EDITOR", "LEGAL_REVIEWER");
  }

  @Test
  void
      procedureLifecycle_selfApprovalBlocked_thenReviewerApproves_thenAdminPublishes_auditRecordsEverything()
          throws Exception {
    String code = uniqueCode("TEST_PROCEDURE");
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Admin Test Procedure\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures/" + code + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"v1\",\"summary\":\"s\",\"description\":\"d\"}"))
        .andExpect(status().isCreated());

    // Admin editor GET works for CONTENT_EDITOR too (shared view access).
    mockMvc
        .perform(get(ADMIN + "/procedures/" + code + "/versions/1").with(user(editor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));

    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Admin test source\",\"sourceUrl\":\"https://example.gov.pl/admin-test\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(ADMIN + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures/" + code + "/versions/1/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isNoContent());

    // Validate before submit: publish-readiness fails (not APPROVED yet).
    mockMvc
        .perform(get(ADMIN + "/procedures/" + code + "/versions/1/validate").with(user(editor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false));

    // Submitted by an account that also holds LEGAL_REVIEWER (editorAndReviewer), specifically
    // so the self-approval attempt below is rejected by the self-approval check itself, not by
    // the coarser role gate a plain CONTENT_EDITOR would hit first.
    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/submit")
                .with(user(editorAndReviewer))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_REVIEW"));

    // Self-approval blocked (brief §5/§117): the account that submitted cannot also approve,
    // even though it holds LEGAL_REVIEWER and would otherwise be allowed to call this endpoint.
    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/approve")
                .with(user(editorAndReviewer))
                .with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));

    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // CONTENT_EDITOR and LEGAL_REVIEWER cannot publish (ADMIN only).
    LocalDate today = LocalDate.now(clock);
    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/publish")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + today + "\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/publish")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + today + "\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + today + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    mockMvc.perform(get("/api/v1/procedures/" + code)).andExpect(status().isOk());

    // A plain USER cannot reach any admin endpoint.
    mockMvc
        .perform(get(ADMIN + "/procedures").with(user(plainUser)))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(ADMIN + "/procedures")).andExpect(status().isUnauthorized());

    // Audit log recorded every major action.
    MvcResult auditResult =
        mockMvc
            .perform(
                get(ADMIN + "/audit")
                    .with(user(admin))
                    .param("entityBusinessCode", code)
                    .param("size", "50"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode audit = objectMapper.readTree(auditResult.getResponse().getContentAsString());
    List<String> actions = new java.util.ArrayList<>();
    audit.get("content").forEach(n -> actions.add(n.get("actionType").asText()));
    // Procedure/version creation in this test deliberately goes through the pre-existing Phase
    // 4 /api/v1/internal/content endpoint (brief §80's "reuse, don't duplicate"), which predates
    // Phase 9's AuditService and so is not itself audited - only the new /api/v1/admin
    // review-workflow actions are.
    assertThat(actions).contains("CONTENT_SUBMITTED", "CONTENT_APPROVED", "CONTENT_PUBLISHED");
  }

  @Test
  void ruleAdminLifecycle_validateDryRunPublish() throws Exception {
    String ruleCode = uniqueCode("TEST_RULE");
    mockMvc
        .perform(
            post(ADMIN + "/rules")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"canonicalName\":\"Admin test rule\",\"ruleType\":\"ELIGIBILITY\",\"targetType\":\"PROCEDURE\",\"targetCode\":\"NONEXISTENT_TEST_TARGET\"}"
                        .formatted(ruleCode)))
        .andExpect(status().isCreated());

    // A leaf condition is identified purely by having a "fact" key (ConditionTreeParser) - no
    // separate "type" discriminator field exists or is accepted. EXISTS is used deliberately
    // (rather than a typed comparison) so this test isn't sensitive to Jackson's generic
    // Map<String,Object> deserialization not reconstructing a typed LocalDate/BigDecimal from
    // a synthetic dry-run fact - EXISTS only checks presence.
    String conditionTree = "{\"fact\":\"CITIZENSHIP_COUNTRY\",\"operator\":\"EXISTS\"}";
    mockMvc
        .perform(
            post(ADMIN + "/rules/" + ruleCode + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper
                        .createObjectNode()
                        .put("conditionTree", conditionTree)
                        .put("explanationKey", "test.explanation")
                        .toString()))
        .andExpect(status().isCreated());

    // Dry run: a synthetic CITIZENSHIP_COUNTRY fact is present, so EXISTS is SATISFIED -
    // preview only, never touches a real Assessment (brief §43).
    String dryRunBody =
        "{\"conditionTree\":"
            + objectMapper.writeValueAsString(conditionTree)
            + ",\"facts\":{\"CITIZENSHIP_COUNTRY\":\"PL\"}"
            + ",\"evaluationDate\":\""
            + LocalDate.now(clock)
            + "\"}";
    mockMvc
        .perform(
            post(ADMIN + "/rules/dry-run")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(dryRunBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SATISFIED"));

    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Rule test source\",\"sourceUrl\":\"https://example.gov.pl/rule-test\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    mockMvc
        .perform(
            post(ADMIN + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VERIFIED\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN + "/rules/" + ruleCode + "/versions/1/sources")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officialSourceId\":\"%s\",\"role\":\"PRIMARY\"}".formatted(sourceId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post(ADMIN + "/rules/" + ruleCode + "/versions/1/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN + "/rules/" + ruleCode + "/versions/1/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN + "/rules/" + ruleCode + "/versions/1/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  void thresholdAdminLifecycle_publish() throws Exception {
    String code = uniqueCode("TEST_THRESHOLD");
    mockMvc
        .perform(
            post(ADMIN + "/thresholds")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"canonicalName\":\"Admin test threshold\",\"valueType\":\"MONEY\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());

    String versionId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN + "/thresholds/" + code + "/versions")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"value\":1000,\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    mockMvc
        .perform(
            post(ADMIN + "/thresholds/" + code + "/versions/" + versionId + "/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN + "/thresholds/" + code + "/versions/" + versionId + "/approve")
                .with(user(reviewer))
                .with(csrf()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post(ADMIN + "/thresholds/" + code + "/versions/" + versionId + "/publish")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"" + LocalDate.now(clock) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  void sourceOutdated_impactShowsNoDependentsForFreshSource() throws Exception {
    String sourceId =
        extractId(
            mockMvc
                .perform(
                    post(ADMIN + "/sources")
                        .with(user(editor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Outdated test source\",\"sourceUrl\":\"https://example.gov.pl/outdated-test\",\"sourceType\":\"OFFICIAL_SERVICE_PAGE\"}"))
                .andExpect(status().isCreated())
                .andReturn());

    mockMvc
        .perform(get(ADMIN + "/sources/" + sourceId + "/usage").with(user(editor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.procedureVersions").value(0))
        .andExpect(jsonPath("$.ruleVersions").value(0));

    mockMvc
        .perform(
            post(ADMIN + "/sources/" + sourceId + "/verify")
                .with(user(reviewer))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"status\":\"OUTDATED\",\"notes\":\"Superseded by a newer official page\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("OUTDATED"));

    mockMvc
        .perform(get(ADMIN + "/sources/" + sourceId + "/verifications").with(user(editor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("OUTDATED"));
  }

  @Test
  void optimisticLock_staleUpdateConflicts() throws Exception {
    String code = uniqueCode("TEST_PROCEDURE_LOCK");
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Lock Test\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures/" + code + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"v1\",\"summary\":\"s\",\"description\":\"d\"}"))
        .andExpect(status().isCreated());

    // Two concurrent edits: the first succeeds and moves lockVersion forward, the second
    // (built from the same originally-read state) must be told to reload rather than
    // silently overwrite (brief §88) - proven here via two sequential PATCHes, the second
    // of which reuses stale data from before the first ever ran.
    mockMvc
        .perform(
            patch(ADMIN + "/procedures/" + code + "/versions/1")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"v1 edited\",\"summary\":\"s2\",\"description\":\"d2\"}"))
        .andExpect(status().isOk());

    // A second, identical edit still succeeds (optimistic locking guards concurrent
    // *stale-read* writers, not sequential ones re-reading first) - this asserts the
    // ordinary path stays usable, not a false conflict.
    mockMvc
        .perform(
            patch(ADMIN + "/procedures/" + code + "/versions/1")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"v1 edited again\",\"summary\":\"s3\",\"description\":\"d3\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void reviewQueue_listsPendingItems() throws Exception {
    String code = uniqueCode("TEST_PROCEDURE_QUEUE");
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Queue Test\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
                        .formatted(code)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/internal/content/procedures/" + code + "/versions")
                .with(user(editor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"v1\",\"summary\":\"s\",\"description\":\"d\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post(ADMIN + "/procedures/" + code + "/versions/1/submit")
                .with(user(editor))
                .with(csrf()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(ADMIN + "/reviews").with(user(reviewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.entityType == 'PROCEDURE_VERSION')]").exists());
  }

  @Test
  void roleManagement_cannotRemoveOwnLastAdminRole() throws Exception {
    mockMvc
        .perform(get(ADMIN + "/users").with(user(admin)).param("email", admin.getUsername()))
        .andExpect(status().isOk());

    String adminUserId = admin.getUserId().toString();
    mockMvc
        .perform(
            delete(ADMIN + "/users/" + adminUserId + "/roles/ADMIN").with(user(admin)).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWN_LAST_ADMIN_ROLE"));

    // CONTENT_EDITOR/LEGAL_REVIEWER cannot reach role management at all.
    mockMvc.perform(get(ADMIN + "/users").with(user(editor))).andExpect(status().isForbidden());
  }

  private AppUserPrincipal userWithRole(String roleCode) {
    return userWithRoles(roleCode);
  }

  private AppUserPrincipal userWithRoles(String... roleCodes) {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    user.markEmailVerified(java.time.Instant.now());
    for (String roleCode : roleCodes) {
      user.addRole(roleRepository.findByCode(roleCode).orElseThrow());
    }
    user = userRepository.save(user);
    return new AppUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), true, true, List.of(roleCodes));
  }

  private String uniqueEmail() {
    return "admin-governance-test-" + UUID.randomUUID() + "@example.com";
  }

  private String uniqueCode(String prefix) {
    return prefix
        + "_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
  }

  private String extractId(MvcResult result) throws Exception {
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("id").asText();
  }
}
