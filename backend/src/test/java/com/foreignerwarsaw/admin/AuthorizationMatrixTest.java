package com.foreignerwarsaw.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) brief §32/§71/§73 - a consolidated, cross-cutting
 * authorization regression, one matrix rather than duplicating the per-controller role checks every
 * admin controller test already carries individually ({@code ProcedureAdminApiSecurityTest}, {@code
 * AdminGovernanceIntegrationTest}'s embedded checks). Representative endpoints only (brief
 * §32/§73's own "do not mechanically duplicate all 69 Admin endpoints") - one per boundary category
 * this codebase actually has: an own-resource endpoint any authenticated role may reach, a privacy
 * endpoint (never bypassed by ADMIN - brief §33/§75), a CONTENT_EDITOR-gated mutation, an
 * ADMIN-only user-management endpoint, and an ADMIN-only audit endpoint.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: this class's exclusively {@code with(user(...))}/ {@code
 * with(csrf())} MockMvc-postprocessor style, real and reproducible this phase (a full {@code ./mvnw
 * verify} run), otherwise leaves the shared {@code CookieCsrfTokenRepository} state such that
 * whichever class runs next in the same cached Spring context (alphabetically, {@code
 * auth.AuthIntegrationTest}) loses its real cookie-based CSRF flow - the exact same class of
 * pollution {@code AdminGovernanceIntegrationTest}'s own Javadoc already documents for the same
 * reason, now confirmed to reproduce deterministically (not a one-off flake) whenever a
 * `with(user())`-only class without this annotation runs immediately before a real-cookie-flow
 * class in the suite.
 */
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorizationMatrixTest extends AbstractIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;

  private AppUserPrincipal userPrincipal;
  private AppUserPrincipal editorPrincipal;
  private AppUserPrincipal reviewerPrincipal;
  private AppUserPrincipal adminPrincipal;

  @BeforeEach
  void setUpActors() {
    userPrincipal = principalWithRole("USER");
    editorPrincipal = principalWithRole("CONTENT_EDITOR");
    reviewerPrincipal = principalWithRole("LEGAL_REVIEWER");
    adminPrincipal = principalWithRole("ADMIN");
  }

  @Test
  void ownResourceEndpoint_reachableByEveryAuthenticatedRole_neverAnonymous() throws Exception {
    assertStatus(get("/api/v1/users/me"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(get("/api/v1/users/me"), userPrincipal, HttpStatus.OK);
    assertStatus(get("/api/v1/users/me"), editorPrincipal, HttpStatus.OK);
    assertStatus(get("/api/v1/users/me"), reviewerPrincipal, HttpStatus.OK);
    assertStatus(get("/api/v1/users/me"), adminPrincipal, HttpStatus.OK);
  }

  @Test
  void privacyExportEndpoint_reachableByOwnerOnly_neverAnAdminBypass() throws Exception {
    // Brief §33/§75 - no /admin/users/{id}/export exists at all (structurally, not just
    // access-denied) - self-service privacy endpoints only ever operate on the caller's own
    // account, regardless of role, including ADMIN.
    assertStatus(get("/api/v1/account/export"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(get("/api/v1/account/export"), userPrincipal, HttpStatus.OK);
    assertStatus(get("/api/v1/account/export"), adminPrincipal, HttpStatus.OK);
  }

  @Test
  void assessmentStart_reachableByEveryAuthenticatedRole() throws Exception {
    assertStatus(post("/api/v1/assessments"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(post("/api/v1/assessments"), userPrincipal, HttpStatus.OK);
  }

  @Test
  void caseList_reachableByEveryAuthenticatedRole_neverAnonymous() throws Exception {
    assertStatus(get("/api/v1/cases"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(get("/api/v1/cases"), userPrincipal, HttpStatus.OK);
  }

  @Test
  void contentCreation_onlyContentEditorAndAdmin() throws Exception {
    assertStatus(
        contentPost("/api/v1/internal/content/procedures", procedureCreateJson()),
        null,
        HttpStatus.UNAUTHORIZED);
    assertStatus(
        contentPost("/api/v1/internal/content/procedures", procedureCreateJson()),
        userPrincipal,
        HttpStatus.FORBIDDEN);
    assertStatus(
        contentPost("/api/v1/internal/content/procedures", procedureCreateJson()),
        reviewerPrincipal,
        HttpStatus.FORBIDDEN);
    assertStatus(
        contentPost("/api/v1/internal/content/procedures", procedureCreateJson()),
        editorPrincipal,
        HttpStatus.CREATED);
    assertStatus(
        contentPost("/api/v1/internal/content/procedures", procedureCreateJson()),
        adminPrincipal,
        HttpStatus.CREATED);
  }

  private String procedureCreateJson() {
    return "{\"code\":\"%s\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Test\",\"shortDescription\":\"For automated tests only\",\"jurisdictionScope\":\"NATIONAL\"}"
        .formatted(uniqueCode());
  }

  @Test
  void userManagement_adminOnly() throws Exception {
    assertStatus(get("/api/v1/admin/users"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(get("/api/v1/admin/users"), userPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/users"), editorPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/users"), reviewerPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/users"), adminPrincipal, HttpStatus.OK);
  }

  @Test
  void auditLog_adminOnly() throws Exception {
    assertStatus(get("/api/v1/admin/audit"), null, HttpStatus.UNAUTHORIZED);
    assertStatus(get("/api/v1/admin/audit"), userPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/audit"), editorPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/audit"), reviewerPrincipal, HttpStatus.FORBIDDEN);
    assertStatus(get("/api/v1/admin/audit"), adminPrincipal, HttpStatus.OK);
  }

  // --- helpers ---

  private MockHttpServletRequestBuilder contentPost(String path, String body) {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void assertStatus(
      MockHttpServletRequestBuilder request, AppUserPrincipal principal, HttpStatus expected)
      throws Exception {
    var builder = request.with(csrf());
    if (principal != null) {
      builder = builder.with(user(principal));
    }
    mockMvc.perform(builder).andExpect(status().is(expected.value()));
  }

  private AppUserPrincipal principalWithRole(String roleCode) {
    User u = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    u.markEmailVerified(Instant.now());
    Role role = roleRepository.findByCode(roleCode).orElseThrow();
    u.addRole(role);
    u = userRepository.save(u);
    return new AppUserPrincipal(
        u.getId(), u.getEmail(), u.getPasswordHash(), true, true, List.of(roleCode));
  }

  private String uniqueEmail() {
    return "authz-matrix-" + UUID.randomUUID() + "@example.com";
  }

  private String uniqueCode() {
    return "TEST_AUTHZ_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
  }
}
