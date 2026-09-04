package com.foreignerwarsaw.procedure.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Role-based access to the internal content-management API (brief §44/§79) - USER forbidden,
 * CONTENT_EDITOR allowed to create draft content, LEGAL_REVIEWER restricted to review/approve,
 * ADMIN allowed everything, and CSRF still enforced for every one of them. Uses {@code
 * with(user(...))} to populate the authenticated principal directly (the register/verify/login
 * roundtrip is already fully covered by {@code AuthIntegrationTest}) while still exercising the
 * real {@code authorizeHttpRequests} matchers/{@code AccessDeniedHandler} in {@code SecurityConfig}
 * - this test is about authorization, not authentication.
 */
// @DirtiesContext(AFTER_CLASS): see RecommendationEngineIntegrationTest's identical Javadoc -
// same real, reproduced-this-phase CookieCsrfTokenRepository pollution pattern.
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProcedureAdminApiSecurityTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/internal/content";

  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;

  /**
   * A principal with no backing {@link User} row at all - fine for every test below that only needs
   * to prove a {@code SecurityConfig} matcher rejects the request before the controller body (and
   * any {@code AuditService} actor lookup) ever runs.
   */
  private AppUserPrincipal principalWithRole(String roleCode) {
    return new AppUserPrincipal(
        UUID.randomUUID(), "test@example.com", "hash", true, true, List.of(roleCode));
  }

  /**
   * Pre-Phase-10 hardening (brief §B) gave {@code ProcedureAdminController#createProcedure} a real
   * audit-log write, which resolves the principal to a real {@link User} row
   * (UserAccountService#getById) - a fake, unpersisted principal now 500s instead of exercising the
   * 201 path this test wants to prove. Only the tests that actually expect a 201 need this; every
   * forbidden-outcome test below is rejected by SecurityConfig before the controller body (and thus
   * the audit write) ever runs, so {@link #principalWithRole} stays fine for those.
   */
  private AppUserPrincipal realPrincipalWithRole(String roleCode) {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    user.markEmailVerified(java.time.Instant.now());
    Role role = roleRepository.findByCode(roleCode).orElseThrow();
    user.addRole(role);
    user = userRepository.save(user);
    return new AppUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), true, true, List.of(roleCode));
  }

  private String uniqueEmail() {
    return "procedure-admin-security-test-" + UUID.randomUUID() + "@example.com";
  }

  private String createProcedureBody() {
    return "{\"code\":\"TEST_SEC_"
        + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        + "\",\"categoryCode\":\"OTHER\",\"canonicalName\":\"Test\",\"jurisdictionScope\":\"NATIONAL\"}";
  }

  @Test
  void unauthenticated_withoutCsrf_isForbidden() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void plainUser_isForbiddenFromCreatingAProcedure() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(principalWithRole("USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void contentEditor_canCreateAProcedure() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(realPrincipalWithRole("CONTENT_EDITOR")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isCreated());
  }

  @Test
  void admin_canAlsoCreateAProcedure_broaderControl() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(realPrincipalWithRole("ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isCreated());
  }

  @Test
  void legalReviewer_isForbiddenFromCreatingAProcedure_reviewOnlyResponsibility() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(principalWithRole("LEGAL_REVIEWER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void contentEditor_isForbiddenFromPublishing_adminOnlyResponsibility() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures/SOME_CODE/versions/1/publish")
                .with(user(principalWithRole("CONTENT_EDITOR")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"2026-01-01\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void legalReviewer_isForbiddenFromPublishing_approveIsNotPublish() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures/SOME_CODE/versions/1/publish")
                .with(user(principalWithRole("LEGAL_REVIEWER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveFrom\":\"2026-01-01\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void contentEditor_isForbiddenFromApproving_reviewIsLegalReviewersJob() throws Exception {
    mockMvc
        .perform(
            post(BASE + "/procedures/SOME_CODE/versions/1/approve")
                .with(user(principalWithRole("CONTENT_EDITOR")))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrfIsStillEnforced_evenForAnAdminWithTheRightRole() throws Exception {
    // Valid role, no CSRF token - still rejected (brief §79: "CSRF still enforced for
    // unsafe cookie-authenticated operations").
    mockMvc
        .perform(
            post(BASE + "/procedures")
                .with(user(principalWithRole("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProcedureBody()))
        .andExpect(status().isForbidden());
  }
}
