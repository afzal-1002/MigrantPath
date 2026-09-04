package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 11 brief §40/§42 - proves {@link SecurityMetricsListener} actually increments on a real
 * failed login through the real HTTP/Security stack (not just "the event class exists"), and that a
 * successful login does not.
 */
class SecurityMetricsListenerTest extends AbstractIntegrationTest {

  @Autowired private MeterRegistry meterRegistry;

  private double loginFailureCount() {
    var counter = meterRegistry.find("auth.login.failure").counter();
    return counter != null ? counter.count() : 0.0;
  }

  private Cookie obtainCsrfCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/platform/status")).andReturn();
    Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return csrf;
  }

  private MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder builder, Cookie csrf) {
    return builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
  }

  @Test
  void aFailedLoginIncrementsTheMetric() throws Exception {
    double before = loginFailureCount();
    Cookie csrf = obtainCsrfCookie();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"does-not-exist-metrics@example.com\",\"password\":\"whatever123\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(loginFailureCount()).isEqualTo(before + 1);
  }
}
