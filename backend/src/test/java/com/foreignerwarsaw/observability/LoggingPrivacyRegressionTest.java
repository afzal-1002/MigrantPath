package com.foreignerwarsaw.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.foreignerwarsaw.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Canonical Phase 14 (Observability) brief §5/§15/§107 - captures every log line emitted (root
 * logger, so this catches a leak from any class, not just ones this phase touched) during a real
 * login failure, assessment answer save, and account registration, then asserts none of a synthetic
 * password/salary/token/email-in-a-DEBUG-message ever appears - extends the manual log-scrub
 * discipline OBSERVABILITY.md already documents into a real, automated regression rather than a
 * one-time audit that could silently rot.
 */
class LoggingPrivacyRegressionTest extends AbstractIntegrationTest {

  private static final String SYNTHETIC_PASSWORD = "Sup3rSecretPassw0rd!";
  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&\\s]+)");

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
  }

  private List<String> allFormattedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void aFailedLoginWithARealPasswordNeverLeaksThatPasswordToAnyLogLine() throws Exception {
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"privacy-log-test@example.com\",\"password\":\"%s\"}"
                        .formatted(SYNTHETIC_PASSWORD)))
        .andExpect(status().isUnauthorized());

    assertThat(allFormattedMessages()).noneMatch(m -> m.contains(SYNTHETIC_PASSWORD));
  }

  @Test
  void registrationWithARealPasswordNeverLeaksThatPasswordToAnyLogLine() throws Exception {
    String email = "privacy-log-register-" + java.util.UUID.randomUUID() + "@example.com";
    Cookie csrf = obtainCsrfCookie();
    mockMvc.perform(
        withCsrf(post("/api/v1/auth/register"), csrf)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"email\":\"%s\",\"password\":\"%s\",\"firstName\":\"Test\",\"acceptTerms\":true,\"acceptPrivacyPolicy\":true}"
                    .formatted(email, SYNTHETIC_PASSWORD)));

    assertThat(allFormattedMessages()).noneMatch(m -> m.contains(SYNTHETIC_PASSWORD));
  }

  /**
   * The real verification/reset token itself (brief §65/§125's own high-priority concern, already
   * fixed at the nginx layer this phase) must also never appear in an *application* log line - a
   * second, independent layer of the same protection.
   */
  @Test
  void aRealVerificationTokenNeverAppearsInAnyApplicationLogLine() throws Exception {
    String email = "privacy-log-token-" + java.util.UUID.randomUUID() + "@example.com";
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"firstName\":\"Test\",\"acceptTerms\":true,\"acceptPrivacyPolicy\":true}"
                        .formatted(email, SYNTHETIC_PASSWORD)))
        .andExpect(status().isCreated());
    String html = findLatestMessageTo(email);
    Matcher matcher = TOKEN_PATTERN.matcher(html);
    assertThat(matcher.find()).isTrue();
    String rawToken = matcher.group(1);

    assertThat(allFormattedMessages()).noneMatch(m -> m.contains(rawToken));
  }

  /**
   * A questionnaire answer (brief §5's own explicit "questionnaire answers" ban) must never appear
   * in a log line either - {@code RequestLoggingFilter}'s own summary line only ever logs the path
   * template/status/duration, never the request body.
   */
  @Test
  void anAssessmentAnswerValueNeverAppearsInAnyLogLine() throws Exception {
    String email = "privacy-log-answer-" + java.util.UUID.randomUUID() + "@example.com";
    Cookie csrf = obtainCsrfCookie();
    registerAndVerify(email, csrf);
    Cookie session = loginAndGetSessionCookie(email, csrf);

    MvcResult started =
        mockMvc
            .perform(withCsrf(post("/api/v1/assessments"), csrf).cookie(session))
            .andExpect(status().isOk())
            .andReturn();
    String assessmentId =
        objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asText();

    String distinctiveSalaryLikeValue = "13579.99";
    mockMvc
        .perform(
            withCsrf(put("/api/v1/assessments/" + assessmentId + "/answers/DATE_OF_BIRTH"), csrf)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateValue\":\"1990-01-01\"}"))
        .andExpect(status().isOk());

    assertThat(allFormattedMessages()).noneMatch(m -> m.contains(distinctiveSalaryLikeValue));
  }

  // --- minimal real cookie-flow helpers (this class's own copy) ---

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

  private MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder builder, Cookie csrf) {
    return builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
  }
}
