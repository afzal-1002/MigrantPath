package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Canonical Phase 14 (Observability) brief §7/§8/§106 - {@link CorrelationIdFilter} was real and
 * tested for MDC-threading since Phase 11, but nothing previously proved the two user/support-
 * facing surfaces this phase adds: the {@code X-Correlation-ID} response header (pre-existing) and
 * the same id now inside every {@link com.foreignerwarsaw.common.web.ApiError} body (new).
 */
class CorrelationIdIntegrationTest extends AbstractIntegrationTest {

  @Test
  void everyResponseCarriesACorrelationIdHeader_generatedWhenNoneSupplied() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/platform/status")).andReturn();
    String correlationId = result.getResponse().getHeader("X-Correlation-ID");
    assertThat(correlationId).isNotBlank();
  }

  @Test
  void aSafeIncomingCorrelationIdIsHonoredVerbatim() throws Exception {
    String supplied = "a-safe-caller-supplied-id-123";
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/platform/status").header("X-Correlation-ID", supplied))
            .andReturn();
    assertThat(result.getResponse().getHeader("X-Correlation-ID")).isEqualTo(supplied);
  }

  @Test
  void anUnsafeIncomingCorrelationIdIsReplaced_neverTrustedVerbatim() throws Exception {
    // Brief §7's own "reject/replace absurdly long/malformed value" - CorrelationIdFilter's
    // own SAFE_ID pattern rejects anything containing a character outside [A-Za-z0-9-] or
    // longer than 100 chars.
    String malicious = "not-safe; DROP TABLE users; --";
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/platform/status").header("X-Correlation-ID", malicious))
            .andReturn();
    String returned = result.getResponse().getHeader("X-Correlation-ID");
    assertThat(returned).isNotEqualTo(malicious);
    assertThat(returned).matches("^[A-Za-z0-9-]{1,100}$");
  }

  /**
   * The decisive new behavior this phase adds (brief §8) - a real error response body must carry
   * the same correlation id a user/support agent can quote back, not just the header a browser
   * devtools panel might show but a phone/email support conversation never will.
   */
  @Test
  void anErrorResponseBodyCarriesTheSameCorrelationIdAsTheResponseHeader() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/does-not-exist-anywhere")).andReturn();
    String headerCorrelationId = result.getResponse().getHeader("X-Correlation-ID");
    String body = result.getResponse().getContentAsString();

    assertThat(headerCorrelationId).isNotBlank();
    assertThat(body).contains("\"correlationId\":\"" + headerCorrelationId + "\"");
  }
}
