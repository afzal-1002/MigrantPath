package com.foreignerwarsaw.reference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Public reference-data API through the real Spring Security filter chain (brief §38) - proves
 * these endpoints are reachable with no session/CSRF at all, that Phase 2's protected endpoints are
 * completely unaffected by adding {@code /api/v1/reference/**} to {@code PUBLIC_GET_ENDPOINTS}, and
 * that no write capability was accidentally exposed under that prefix (brief §66/§76).
 */
class ReferenceApiIntegrationTest extends AbstractIntegrationTest {

  // --- Public, unauthenticated access ---

  @Test
  void countryList_isReachableWithNoSessionAndNoCsrfToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/countries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(250));
  }

  @Test
  void countryDetail_pl_showsItsActiveGroupsAsOfToday() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/countries/PL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PL"))
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups").value(org.hamcrest.Matchers.hasItem("EU_MEMBER")))
        .andExpect(jsonPath("$.codeStandard").value("ISO_3166_1"))
        .andExpect(jsonPath("$.officiallyAssigned").value(true));
  }

  @Test
  void countryDetail_xk_isHonestlyFlaggedAsNotOfficiallyAssigned() throws Exception {
    // Post-approval audit fix: Kosovo is supported (kept, not removed) but must never
    // be presented through the API as an official ISO 3166-1 code.
    mockMvc
        .perform(get("/api/v1/reference/countries/XK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("XK"))
        .andExpect(jsonPath("$.codeStandard").value("USER_ASSIGNED"))
        .andExpect(jsonPath("$.officiallyAssigned").value(false));
  }

  @Test
  void countryDetail_unknownCode_returns404WithCountryNotFound() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/countries/ZZ"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COUNTRY_NOT_FOUND"));
  }

  @Test
  void regionsForPoland_returnsAllSixteenVoivodeships() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/countries/PL/regions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(16));
  }

  @Test
  void citiesForMazowieckie_returnsOnlyWarsaw() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/regions/MAZOWIECKIE/cities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("WARSAW"));
  }

  @Test
  void districtsForWarsaw_returnsAllEighteenOfficialDistricts() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/cities/WARSAW/districts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(18));
  }

  @Test
  void authorities_returnsAllThreeSeedAuthoritiesAcrossJurisdictionLevels() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/authorities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(
            jsonPath("$[*].code")
                .value(
                    org.hamcrest.Matchers.containsInAnyOrder(
                        "UDSC", "MAZOWIECKIE_VOIVODESHIP_OFFICE", "WARSAW_CITY_HALL")));
  }

  @Test
  void offices_returnsTheOneVerifiedSeedOfficeWithItsServices() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/offices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("MAZOWIECKIE_WSC_MARSZALKOWSKA"))
        .andExpect(
            jsonPath("$[0].services")
                .value(org.hamcrest.Matchers.hasItem("IMMIGRATION_INFORMATION")));
  }

  @Test
  void offices_filteredByUnknownService_returnsEmptyListNot404() throws Exception {
    mockMvc
        .perform(get("/api/v1/reference/offices").param("service", "NOPE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- No write capability exists under this prefix (brief §66) ---

  @Test
  void unauthenticatedPostToReferencePrefix_isRejectedNotSilentlyRoutedAnywhere() throws Exception {
    // PUBLIC_GET_ENDPOINTS only permits GET - POST to any /api/v1/reference/** path is
    // never permitAll'd. It's rejected with 403 (CSRF runs before authorization in the
    // filter chain - the same ordering AuthIntegrationTest's own CSRF tests document),
    // not silently routed anywhere: no controller mapping for POST exists under this
    // prefix at all in Phase 3.
    mockMvc
        .perform(
            post("/api/v1/reference/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  // --- Phase 2 regression: protected endpoints are unaffected ---

  @Test
  void phase2ProtectedEndpoint_stillRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void phase2PublicPlatformStatus_stillWorks() throws Exception {
    mockMvc.perform(get("/api/v1/platform/status")).andExpect(status().isOk());
  }
}
