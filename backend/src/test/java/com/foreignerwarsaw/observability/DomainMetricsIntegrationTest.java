package com.foreignerwarsaw.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Canonical Phase 14 (Observability) brief §109 - a real, full HTTP guided flow (register → verify
 * → login → assessment → complete → recommendation-run), asserting the two metrics that do not
 * require any published legal content to exist to fire (this codebase's own production
 * Rules/Procedures are authored exclusively through the real Admin governance workflow, never
 * seeded by a Flyway migration - confirmed by grepping every migration file for one - so a fresh
 * Testcontainers database this test runs against, unlike the shared persistent dev database, has
 * zero active Rules; a real, useful finding this test's own construction surfaced, not a defect).
 * {@code rule.evaluation} and {@code case.creation} both need a real published Rule/Procedure to
 * exercise meaningfully and are instead verified directly against the shared persistent dev
 * database as part of this phase's production-like verification pass (docs/product/
 * PHASE_14_REPORT.md) - building the full multi-actor Admin-governance fixture other test classes
 * already carry (e.g. {@code AccountPrivacyIntegrationTest}'s {@code publishRule}/ {@code
 * createPublishedProcedure}) here too would duplicate ~150 lines of already-covered, unrelated
 * Admin-workflow test surface for no new coverage of the actual thing this test exists to check
 * (metric wiring).
 */
class DomainMetricsIntegrationTest extends AbstractIntegrationTest {

  private static final String ASSESSMENTS_BASE = "/api/v1/assessments";
  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&\\s]+)");

  @Autowired private MeterRegistry meterRegistry;

  private double count(String name) {
    var counter = meterRegistry.find(name).counter();
    return counter != null ? counter.count() : 0.0;
  }

  @Test
  void completingARealAssessment_incrementsAssessmentCompletedMetric() throws Exception {
    double before = count("assessment.completed");

    String email = "metrics-assessment-" + java.util.UUID.randomUUID() + "@example.com";
    Cookie csrf = obtainCsrfCookie();
    registerAndVerify(email, csrf);
    Cookie session = loginAndGetSessionCookie(email, csrf);

    MvcResult started =
        mockMvc
            .perform(withCsrf(post(ASSESSMENTS_BASE), csrf).cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    String assessmentId = extractId(started);

    answer(assessmentId, csrf, session, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(assessmentId, csrf, session, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(assessmentId, csrf, session, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(assessmentId, csrf, session, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(
        assessmentId,
        csrf,
        session,
        "PRIMARY_PURPOSE",
        "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");

    mockMvc
        .perform(
            withCsrf(post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete"), csrf)
                .cookie(session))
        .andExpect(status().isOk());

    assertThat(count("assessment.completed")).isEqualTo(before + 1);
  }

  /**
   * Even with zero active Rules in this fresh database (see class Javadoc), {@link
   * com.foreignerwarsaw.recommendation.engine.RecommendationService#analyze} still creates and
   * completes a real {@code RecommendationRun} (COMPLETED, with an empty candidate list) - proving
   * the metric fires on the actual run-completion code path, not only when a candidate exists.
   */
  @Test
  void analyzingACompletedAssessment_incrementsARecommendationRunOutcomeMetric() throws Exception {
    double before =
        count("recommendation.completed")
            + count("recommendation.partial")
            + count("recommendation.failed");

    String email = "metrics-recommend-" + java.util.UUID.randomUUID() + "@example.com";
    Cookie csrf = obtainCsrfCookie();
    registerAndVerify(email, csrf);
    Cookie session = loginAndGetSessionCookie(email, csrf);

    MvcResult started =
        mockMvc
            .perform(withCsrf(post(ASSESSMENTS_BASE), csrf).cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    String assessmentId = extractId(started);
    answer(assessmentId, csrf, session, "CITIZENSHIP_COUNTRY", "{\"referenceCode\":\"PK\"}");
    answer(assessmentId, csrf, session, "CURRENTLY_IN_POLAND", "{\"booleanValue\":true}");
    answer(assessmentId, csrf, session, "CURRENT_LEGAL_STATUS", "{\"referenceCode\":\"NONE\"}");
    answer(assessmentId, csrf, session, "DATE_OF_BIRTH", "{\"dateValue\":\"1990-01-01\"}");
    answer(
        assessmentId,
        csrf,
        session,
        "PRIMARY_PURPOSE",
        "{\"selectedOptionCodes\":[\"GET_PESEL\"]}");
    mockMvc
        .perform(
            withCsrf(post(ASSESSMENTS_BASE + "/" + assessmentId + "/complete"), csrf)
                .cookie(session))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            withCsrf(post(ASSESSMENTS_BASE + "/" + assessmentId + "/recommendation-runs"), csrf)
                .cookie(session))
        .andExpect(status().isOk());

    double after =
        count("recommendation.completed")
            + count("recommendation.partial")
            + count("recommendation.failed");
    assertThat(after).isEqualTo(before + 1);
  }

  // --- minimal real cookie-flow helpers (this class's own copy - brief §54's own established
  // per-test-class convention, see AccountPrivacyIntegrationTest) ---

  private Cookie obtainCsrfCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/platform/status")).andReturn();
    Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return csrf;
  }

  private void registerAndVerify(String email, Cookie csrf) throws Exception {
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"correct-horse-battery\",\"firstName\":\"Test\",\"acceptTerms\":true,\"acceptPrivacyPolicy\":true}"
                        .formatted(email)))
        .andExpect(status().isCreated());
    String html = findLatestMessageTo(email);
    Matcher matcher = TOKEN_PATTERN.matcher(html);
    assertThat(matcher.find()).isTrue();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/verify-email"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + matcher.group(1) + "\"}"))
        .andExpect(status().isOk());
  }

  private Cookie loginAndGetSessionCookie(String email, Cookie csrf) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/login"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"correct-horse-battery\"}"
                            .formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    return sessionCookie;
  }

  private void answer(
      String assessmentId, Cookie csrf, Cookie session, String questionCode, String bodyJson)
      throws Exception {
    mockMvc
        .perform(
            withCsrf(put(ASSESSMENTS_BASE + "/" + assessmentId + "/answers/" + questionCode), csrf)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
        .andExpect(status().isOk());
  }

  private String extractId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder builder, Cookie csrf) {
    return builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
  }
}
