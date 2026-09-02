package com.foreignerwarsaw.procedure.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.procedure.category.ProcedureCategoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Public read API through the real Spring Security filter chain (brief §79) - proves DRAFT content
 * is never returned publicly no matter how directly it's queried, and that these endpoints require
 * no session/CSRF at all.
 */
class ProcedureApiIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ProcedureRepository procedureRepository;
  @Autowired private ProcedureCategoryRepository procedureCategoryRepository;
  @Autowired private ProcedureVersionRepository procedureVersionRepository;

  @Test
  void list_isReachableWithNoSessionAndNoCsrfToken() throws Exception {
    mockMvc.perform(get("/api/v1/procedures")).andExpect(status().isOk());
  }

  @Test
  void detail_unknownCode_returns404() throws Exception {
    mockMvc
        .perform(get("/api/v1/procedures/DOES_NOT_EXIST"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROCEDURE_NOT_FOUND"));
  }

  @Test
  void detail_procedureWithOnlyADraftVersion_returns404_notTheDraftContent() throws Exception {
    // The exact case brief §29/§79 exist to prevent: DRAFT content must never be
    // reachable from the public API under any code path.
    var category = procedureCategoryRepository.findByCodeIgnoreCase("OTHER").orElseThrow();
    var procedure =
        procedureRepository.saveAndFlush(
            Procedure.create(
                "TEST_DRAFT_ONLY_API_" + UUID.randomUUID().toString().substring(0, 8),
                category,
                "Draft-only test procedure",
                "Should never be publicly visible",
                JurisdictionScope.NATIONAL));
    procedureVersionRepository.saveAndFlush(
        ProcedureVersion.draft(procedure, 1, "Secret draft title", "Secret", "Secret", null));

    mockMvc
        .perform(get("/api/v1/procedures/" + procedure.getCode()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROCEDURE_NOT_FOUND"));

    // Also absent from the list.
    mockMvc
        .perform(get("/api/v1/procedures"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == '" + procedure.getCode() + "')]").doesNotExist());
  }

  @Test
  void phase2ProtectedEndpoint_stillRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }
}
