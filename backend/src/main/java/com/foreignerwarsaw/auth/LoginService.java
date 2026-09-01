package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login is a plain {@code @RestController}-driven flow, not Spring Security's default {@code
 * UsernamePasswordAuthenticationFilter} (which expects form-encoded parameters, not a JSON body) -
 * see ADR-005 and SecurityConfig. Two things that filter normally does automatically have to be
 * replicated explicitly here:
 *
 * <ol>
 *   <li>Persisting the {@link SecurityContext} via {@link SecurityContextRepository#saveContext} -
 *       the modern {@code SecurityContextHolderFilter} (which replaced the old, auto-saving {@code
 *       SecurityContextPersistenceFilter} in Spring Security 6+) only <i>reads</i> the context on
 *       incoming requests; it does not save one set programmatically mid-request. Skipping this
 *       would mean login "succeeds" but no session cookie is ever issued.
 *   <li>Session-fixation protection via {@link SessionAuthenticationStrategy#onAuthentication} -
 *       normally invoked by {@code AbstractAuthenticationProcessingFilter} after a successful
 *       authentication, so a login always gets a freshly-issued session ID rather than reusing
 *       whatever pre-login session (if any) the browser presented.
 * </ol>
 *
 * <p>Both are exactly the kind of thing brief §11's "prove it, don't assume it" is about, and are
 * proven by AuthIntegrationTest's login-then-call-/users/me assertion.
 */
@Service
public class LoginService {

  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final SecurityContextRepository securityContextRepository;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityEventLogger securityEventLogger;
  private final AuthProperties authProperties;
  private final Clock clock;

  public LoginService(
      AuthenticationManager authenticationManager,
      UserRepository userRepository,
      SecurityContextRepository securityContextRepository,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityEventLogger securityEventLogger,
      AuthProperties authProperties,
      Clock clock) {
    this.authenticationManager = authenticationManager;
    this.userRepository = userRepository;
    this.securityContextRepository = securityContextRepository;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityEventLogger = securityEventLogger;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  /**
   * On success, establishes the session. On failure, lets the specific {@link
   * org.springframework.security.core.AuthenticationException} subtype (already thrown by Spring
   * Security's {@code DaoAuthenticationProvider} - {@code BadCredentialsException}, {@code
   * LockedException}, or {@code DisabledException}) propagate to {@link
   * com.foreignerwarsaw.common.web.GlobalExceptionHandler} for mapping to the matching error code -
   * only wrong-password/unknown-email failures (brief §21) increment the persistent lockout counter
   * here; a locked or unverified account's *next* attempt doesn't get penalized again for being
   * locked/unverified.
   */
  @Transactional
  public User login(
      String rawEmail, String password, HttpServletRequest request, HttpServletResponse response) {
    String email = RegistrationService.normalizeEmail(rawEmail);
    try {
      Authentication authenticated =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
      return onSuccess(email, authenticated, request, response);
    } catch (BadCredentialsException ex) {
      recordFailedAttempt(email);
      throw ex;
    }
  }

  private User onSuccess(
      String email,
      Authentication authenticated,
      HttpServletRequest request,
      HttpServletResponse response) {
    sessionAuthenticationStrategy.onAuthentication(authenticated, request, response);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authenticated);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () ->
                    new IllegalStateException("Authenticated principal has no matching user row"));
    user.recordSuccessfulLogin(clock.instant());
    securityEventLogger.log(SecurityEventLogger.Event.LOGIN_SUCCESS, user.getId().toString());
    return user;
  }

  private void recordFailedAttempt(String email) {
    userRepository
        .findByEmailIgnoreCase(email)
        .ifPresentOrElse(
            user -> {
              user.incrementFailedLoginAttempts();
              if (user.getFailedLoginAttempts() >= authProperties.maxFailedLoginAttempts()) {
                user.lockUntil(clock.instant().plus(authProperties.lockoutDuration()));
                securityEventLogger.log(
                    SecurityEventLogger.Event.ACCOUNT_LOCKED, user.getId().toString());
              } else {
                securityEventLogger.log(
                    SecurityEventLogger.Event.LOGIN_FAILURE, user.getId().toString());
              }
            },
            () -> securityEventLogger.logUnknownSubject(SecurityEventLogger.Event.LOGIN_FAILURE));
  }
}
