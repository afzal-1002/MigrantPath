package com.foreignerwarsaw.health;

import static org.hamcrest.Matchers.is;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Proves the Phase 1 baseline still holds end to end: the public platform-status endpoint is
 * reachable, the actuator health endpoint is reachable, and everything else requires authentication
 * - not merely "not yet implemented" but actively enforced (brief §28).
 */
class PlatformStatusControllerTest extends AbstractIntegrationTest {

  @Test
  void platformStatusIsPubliclyReachable() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v1/platform/status"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.status", is("UP")))
        .andExpect(MockMvcResultMatchers.jsonPath("$.application", is("Foreigner Warsaw")));
  }

  @Test
  void actuatorHealthIsPubliclyReachable() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/actuator/health"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.status", is("UP")));
  }

  @Test
  void unauthenticatedRequestToNonPublicPathRequiresAuthenticationNotSilentlyOpen()
      throws Exception {
    // 401, not 403 or 404: as of Phase 2's SecurityConfig, an unauthenticated request
    // to any non-public path is rejected as "not authenticated" regardless of whether
    // that path exists - route existence is only discoverable once authenticated (see
    // SecurityConfig's Javadoc / ADR-005 for the full 401 vs 403 vs 404 rationale).
    // This replaces Phase 1's blanket 403.
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v1/does-not-exist-yet"))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized())
        .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }
}
