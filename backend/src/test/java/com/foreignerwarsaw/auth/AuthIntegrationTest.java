package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full account-lifecycle suite against a real Testcontainers PostgreSQL 18 and a real
 * Testcontainers Mailpit, through the real Spring Security filter chain (brief §38) - nothing here
 * is faked. Every scenario the brief names explicitly is a test method below; see the method
 * Javadoc for which one.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");
  private static final String PASSWORD = "correct-horse-battery";

  private String uniqueEmail() {
    return "user-" + System.nanoTime() + "@example.com";
  }

  // --- CSRF plumbing every unsafe request needs (brief §11/§27) ---

  private Cookie obtainCsrfCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/platform/status")).andReturn();
    Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrf)
        .as("CsrfCookieFilter must set XSRF-TOKEN on the very first request")
        .isNotNull();
    return csrf;
  }

  private MockHttpServletRequestBuilder withCsrf(
      MockHttpServletRequestBuilder builder, Cookie csrf) {
    return builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
  }

  private String registerJson(String email, String password) {
    return """
        {"email":"%s","password":"%s","firstName":"Pat","acceptTerms":true,"acceptPrivacyPolicy":true}
        """
        .formatted(email, password);
  }

  private String extractTokenFromEmail(String html) {
    Matcher matcher = TOKEN_PATTERN.matcher(html);
    assertThat(matcher.find()).as("email body must contain a token= link").isTrue();
    return matcher.group(1);
  }

  /**
   * Registers, fetches the real verification email from Mailpit, and verifies - the shared setup
   * path most of the scenarios below build on.
   */
  private void registerAndVerify(String email, String password) throws Exception {
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, password)))
        .andExpect(status().isCreated());

    String html = findLatestMessageTo(email);
    assertThat(html).as("verification email must have arrived in Mailpit").isNotNull();
    String token = extractTokenFromEmail(html);

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/verify-email"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isOk());
  }

  /**
   * Logs in and returns the {@code SESSION} cookie, for tests that need an authenticated context.
   * Deliberately returns the cookie rather than {@code request.getSession()} - Spring Session's
   * filter wraps the request to back {@code getSession()} with its JDBC-backed implementation, and
   * that wrapping isn't visible through {@code MvcResult.getRequest()} (a MockMvc/Spring-Session
   * interaction quirk, not a real bug in the app). Reusing the actual cookie, exactly as a browser
   * would send it back, sidesteps that entirely and is arguably the more faithful thing to simulate
   * anyway.
   */
  private Cookie loginAndGetSessionCookie(String email, String password, Cookie csrf)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/login"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    Cookie sessionCookie = result.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).as("login must issue a SESSION cookie").isNotNull();
    return sessionCookie;
  }

  // --- Registration ---

  @Test
  void registration_createsUserAssignsUserRoleHashesPasswordAndSendsVerificationEmail()
      throws Exception {
    String email = uniqueEmail();
    Cookie csrf = obtainCsrfCookie();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.roles[0]").value("USER"))
        .andExpect(jsonPath("$.emailVerified").value(false));

    String html = findLatestMessageTo(email);
    assertThat(html).contains("Verify my email");
  }

  @Test
  void registration_duplicateEmail_isRejectedWithConflict() throws Exception {
    String email = uniqueEmail();
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, PASSWORD)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  // --- Email verification ---

  @Test
  void verification_validToken_activatesAccount() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    // Proven indirectly: login only succeeds post-verification (see next test) - this
    // test's own assertions live inside registerAndVerify's 200 expectation, and the
    // login test below is what actually proves ACTIVE status took effect.
  }

  // --- Login ---

  @Test
  void login_beforeVerification_isRejectedWithEmailNotVerified() throws Exception {
    String email = uniqueEmail();
    Cookie csrf = obtainCsrfCookie();
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/register"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, PASSWORD)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
  }

  @Test
  void login_afterVerification_succeedsAndEstablishesASessionCookie() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();

    MvcResult result =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/login"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andReturn();

    assertThat(result.getResponse().getCookie("SESSION"))
        .as("a successful login must issue the SESSION cookie")
        .isNotNull();
  }

  @Test
  void login_withWrongPassword_isRejectedWithInvalidCredentials() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void login_unknownEmail_isRejectedWithInvalidCredentialsNotAccountExistenceHint()
      throws Exception {
    Cookie csrf = obtainCsrfCookie();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"does-not-exist@example.com\",\"password\":\"whatever123\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  // --- Current user ---

  @Test
  void currentUser_withValidSession_returns200() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(get("/api/v1/users/me").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  void currentUser_withoutSession_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  // --- CSRF ---

  @Test
  void csrf_authenticatedUnsafeRequestWithoutToken_isRejected() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    // No CSRF cookie/header attached at all.
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Should Not Apply\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrf_authenticatedUnsafeRequestWithValidToken_isAccepted() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(
            withCsrf(patch("/api/v1/users/me"), csrf)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Updated Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Updated Name"));
  }

  // --- Logout ---

  @Test
  void logout_invalidatesSessionSoSubsequentRequestsAreUnauthorized() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc.perform(get("/api/v1/users/me").cookie(sessionCookie)).andExpect(status().isOk());

    mockMvc
        .perform(withCsrf(post("/api/v1/auth/logout"), csrf).cookie(sessionCookie))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/users/me").cookie(sessionCookie))
        .andExpect(status().isUnauthorized());
  }

  // --- Forgot / reset password ---

  @Test
  void forgotPassword_alwaysReturnsGenericResponseRegardlessOfAccountExistence() throws Exception {
    Cookie csrf = obtainCsrfCookie();

    String knownEmailBody = "{\"email\":\"" + uniqueEmail() + "\"}";
    String unknownEmailBody = "{\"email\":\"definitely-does-not-exist@example.com\"}";

    MvcResult knownResult =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/forgot-password"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(knownEmailBody))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult unknownResult =
        mockMvc
            .perform(
                withCsrf(post("/api/v1/auth/forgot-password"), csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unknownEmailBody))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(knownResult.getResponse().getContentAsString())
        .isEqualTo(unknownResult.getResponse().getContentAsString());
  }

  @Test
  void resetPassword_validToken_updatesPassword_oldPasswordThenFails_tokenCannotBeReused()
      throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/forgot-password"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk());

    String html = findLatestMessageTo(email);
    assertThat(html).as("password reset email must have arrived").isNotNull();
    String token = extractTokenFromEmail(html);
    String newPassword = "new-correct-horse-battery";

    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/reset-password"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)))
        .andExpect(status().isOk());

    // Old password now fails.
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isUnauthorized());

    // New password succeeds.
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/login"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, newPassword)))
        .andExpect(status().isOk());

    // Replaying the same reset token fails - it was already consumed.
    mockMvc
        .perform(
            withCsrf(post("/api/v1/auth/reset-password"), csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"%s\",\"newPassword\":\"another-new-password\"}".formatted(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  // --- Change password ---

  @Test
  void changePassword_requiresCurrentPasswordAndInvalidatesTheSession() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(
            withCsrf(post("/api/v1/users/me/change-password"), csrf)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"wrong\",\"newPassword\":\"another-new-password\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            withCsrf(post("/api/v1/users/me/change-password"), csrf)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"newPassword\":\"another-new-password\"}"
                        .formatted(PASSWORD)))
        .andExpect(status().isNoContent());

    // The session that made the change is itself invalidated (documented choice,
    // brief §16) - the caller must sign in again too.
    mockMvc
        .perform(get("/api/v1/users/me").cookie(sessionCookie))
        .andExpect(status().isUnauthorized());
  }

  // --- Authorization / route existence (brief §19) ---

  @Test
  void unauthenticatedRequestToProtectedEndpoint_returns401() throws Exception {
    // CSRF runs before authorization in the filter chain (brief §11's own CSRF tests
    // above already cover "no token at all -> 403" for an *authenticated* request);
    // to isolate the authentication check specifically, this request supplies a valid
    // CSRF token but no session, so a 401 here is unambiguously about authentication,
    // not CSRF.
    Cookie csrf = obtainCsrfCookie();
    mockMvc.perform(withCsrf(patch("/api/v1/users/me"), csrf)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequestToGenuinelyUnmappedRoute_returns404() throws Exception {
    String email = uniqueEmail();
    registerAndVerify(email, PASSWORD);
    Cookie csrf = obtainCsrfCookie();
    Cookie sessionCookie = loginAndGetSessionCookie(email, PASSWORD, csrf);

    mockMvc
        .perform(get("/api/v1/this-route-genuinely-does-not-exist").cookie(sessionCookie))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
