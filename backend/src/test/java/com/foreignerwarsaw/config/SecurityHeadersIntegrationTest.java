package com.foreignerwarsaw.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 brief §16/§150 - proves the security headers {@link SecurityConfig} adds actually reach
 * a real HTTP response, against the real filter chain (not just the YAML/config-file assertions in
 * {@link ProductionConfigTest}, which can't see code-level {@code HttpSecurity} configuration at
 * all). Also re-confirms Spring Security's own defaults (never explicitly configured in this
 * codebase, so easy to accidentally disable) are still present - discovered originally by directly
 * curling the running dev backend, now a permanent regression test instead of a one-off check.
 */
class SecurityHeadersIntegrationTest extends AbstractIntegrationTest {

  @Test
  void publicResponseCarriesEveryExpectedSecurityHeader() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        // Added explicitly by SecurityConfig (Phase 11) - not a Spring Security default.
        .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
        .andExpect(
            header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
        .andExpect(header().string("Referrer-Policy", is("strict-origin-when-cross-origin")))
        .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
        // Spring Security's own defaults, never explicitly configured - asserted here so
        // a future change that accidentally disables headers() is caught.
        .andExpect(header().string("X-Content-Type-Options", is("nosniff")))
        .andExpect(header().string("X-Frame-Options", is("DENY")));
  }

  @Test
  void unencryptedTestRequestNeverCarriesAnHstsHeader() throws Exception {
    // HstsHeaderWriter only ever writes when request.isSecure() is true (Spring
    // Security's own behavior, unmodified here) - this test's MockMvc request is
    // plain HTTP, exactly like a real local/CI request, so this is the honest
    // "HSTS never appears over HTTP" proof brief §11 asks for, not an assumption.
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"));
  }
}
