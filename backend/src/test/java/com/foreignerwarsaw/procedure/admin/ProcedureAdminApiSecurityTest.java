package com.foreignerwarsaw.procedure.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.AppUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class ProcedureAdminApiSecurityTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/internal/content";

  private AppUserPrincipal principalWithRole(String roleCode) {
    return new AppUserPrincipal(
        UUID.randomUUID(), "test@example.com", "hash", true, true, List.of(roleCode));
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
                .with(user(principalWithRole("CONTENT_EDITOR")))
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
                .with(user(principalWithRole("ADMIN")))
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
