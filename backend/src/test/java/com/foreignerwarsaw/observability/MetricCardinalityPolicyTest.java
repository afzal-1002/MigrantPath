package com.foreignerwarsaw.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.AbstractIntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Canonical Phase 14 (Observability) brief §18/§110 - a real, automated policy check, not just a
 * promise kept by code review: no tag key registered anywhere in the real {@link
 * io.micrometer.core.instrument.MeterRegistry} this application builds may be one of the banned,
 * high-cardinality/personal-data names. Scans every meter actually registered after real HTTP
 * traffic (framework metrics like {@code http.server.requests} included) - a genuinely new banned
 * tag introduced anywhere, including by a future Spring Boot upgrade's own auto-instrumentation,
 * fails this test immediately.
 */
class MetricCardinalityPolicyTest extends AbstractIntegrationTest {

  private static final Set<String> BANNED_TAG_KEYS =
      Set.of(
          "userId",
          "user_id",
          "email",
          "caseId",
          "case_id",
          "assessmentId",
          "assessment_id",
          "recommendationRunId",
          "recommendation_run_id",
          "correlationId",
          "correlation_id",
          "source",
          "sourceUrl",
          "source_url");

  @org.springframework.beans.factory.annotation.Autowired
  private io.micrometer.core.instrument.MeterRegistry meterRegistry;

  @Test
  void noRegisteredMeterCarriesABannedHighCardinalityOrPersonalDataTag() throws Exception {
    // Generate at least one real request so http.server.requests (a URI-templated,
    // framework-owned metric) is populated too, not just an empty registry.
    mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/api/v1/platform/status"));

    List<String> violations =
        meterRegistry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(tag -> tag.getKey())
            .distinct()
            .filter(BANNED_TAG_KEYS::contains)
            .toList();

    assertThat(violations)
        .as("banned high-cardinality/personal-data tag keys found on a real registered meter")
        .isEmpty();
  }
}
