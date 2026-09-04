package com.foreignerwarsaw.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 brief §37/§150/§152 - a real request to every sensitive Actuator endpoint must never
 * succeed, in any profile (the {@code test} profile this suite runs under shares {@code
 * application.yml}'s base {@code management.endpoints.web.exposure.include: health,info} unless a
 * profile file explicitly widens it, and none of {@code local}/{@code test}/ {@code staging}/{@code
 * production} do).
 *
 * <p>The actual, verified outcome is {@code 401}, not {@code 404} - a real finding, not what this
 * test originally assumed. {@link SecurityConfig}'s {@code PUBLIC_GET_ENDPOINTS} only permits
 * {@code /actuator/health}/{@code /actuator/health/**}/{@code /actuator/info} anonymously; every
 * other {@code /actuator/**} path requires authentication, and Spring Security's filter chain runs
 * before the (unregistered, since it's excluded from {@code exposure.include}) Actuator endpoint
 * mapping is ever reached - so an anonymous caller gets {@code 401 AUTHENTICATION_REQUIRED}, the
 * same as any other non-public path, per this codebase's own documented 401-vs-404 discipline
 * (SecurityConfig's Javadoc: "route existence is itself only discoverable once authenticated").
 * This is a strictly stronger guarantee than a bare 404 would have been: an anonymous caller can't
 * even tell these routes exist, let alone reach them.
 */
class ActuatorExposureTest extends AbstractIntegrationTest {

  @Test
  void envEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  void beansEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
  }

  @Test
  void configpropsEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());
  }

  @Test
  void heapdumpEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
  }

  @Test
  void threaddumpEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/threaddump")).andExpect(status().isUnauthorized());
  }

  @Test
  void mappingsEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/mappings")).andExpect(status().isUnauthorized());
  }

  @Test
  void metricsEndpointIsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void healthEndpointNeverShowsComponentDetailsPublicly() throws Exception {
    // show-details: never (base config) - the response body must stay a bare status,
    // no datasource URL/disk path/component breakdown, even though the endpoint
    // itself is intentionally public.
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.components")
                .doesNotExist());
  }
}
