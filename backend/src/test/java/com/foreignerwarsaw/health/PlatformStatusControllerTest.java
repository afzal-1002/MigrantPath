package com.foreignerwarsaw.health;

import static org.hamcrest.Matchers.is;

import com.foreignerwarsaw.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Proves the Phase 1 baseline end to end: the public platform-status endpoint is reachable, the
 * actuator health endpoint is reachable, and everything else is denied by the intentionally
 * restrictive Phase 1 {@link com.foreignerwarsaw.config.SecurityConfig} (brief §28) - not merely
 * "not yet implemented" but actively closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformStatusControllerTest {

  @Autowired private MockMvc mockMvc;

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
  void unmappedEndpointsAreDeniedNotSilentlyOpen() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v1/does-not-exist-yet"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
  }
}
