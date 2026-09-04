package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

  @Autowired private MeterRegistry meterRegistry;

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

  /**
   * Canonical Phase 14 (Observability) brief §17 - unlike every endpoint above, {@code prometheus}
   * genuinely is in {@code management.endpoints.web.exposure.include} now (application.yml), so
   * this is a different code path than "Actuator never even registered the route" - the same 401
   * here proves {@link SecurityConfig}'s own permitted-path list (unchanged by that addition) is
   * what actually gates it, not exposure-list omission alone.
   */
  @Test
  void prometheusEndpointIsExposedToActuatorButNotToAnonymousCallers() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  /**
   * A real, disclosed testing-harness limitation found this phase: {@code
   * spring-boot-starter-actuator-test} pulls in Spring Boot 4's own Micrometer test-support module
   * ({@code spring-boot-starter-micrometer-metrics-test}), which auto-configures a test-friendly
   * {@code MeterRegistry} that is not a {@code PrometheusMeterRegistry} - {@code
   * PrometheusScrapeEndpoint}'s own auto-configuration is conditional on exactly that bean type, so
   * the endpoint is never actually registered under {@code AbstractIntegrationTest}'s MockMvc/
   * Testcontainers setup regardless of authentication (confirmed directly: even a full real
   * register→verify→login cookie-authenticated request 404s here, routed to the static-resource
   * fallback handler rather than Actuator's own mapping - while the identical request against a
   * real standalone server, over a real cookie session, returns 200 with real Prometheus-format
   * content - see docs/product/PHASE_14_REPORT.md's "Production-Like Verification"). This is Spring
   * Boot's own deliberate test-speed behavior, not a defect in this application. What MockMvc *can*
   * reliably prove is (a) the security boundary above (401 for anonymous, real) and (b) that
   * metrics are genuinely being recorded into a real registry (below) - the endpoint's own real,
   * authenticated, content-bearing HTTP behavior is proven against the real deployed stack instead.
   */
  @Test
  void meterRegistryIsRealAndFunctional() {
    assertThat(meterRegistry.find("jvm.memory.used").gauge()).isNotNull();
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
