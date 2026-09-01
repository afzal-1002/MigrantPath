package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.VerificationEmailService;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration conflict strategy (brief §7/§46): unlike login/forgot-password/resend- verification,
 * registration DOES reveal "this email is already registered" (409 {@code
 * EMAIL_ALREADY_REGISTERED}) rather than a generic response. This is a deliberate, documented
 * trade-off, not an oversight: an attacker can already probe for a duplicate by attempting a
 * registration and reading the response either way, so a generic response here buys no real
 * enumeration protection while meaningfully hurting legitimate UX (a normal user needs to know
 * their existing account is why signup failed). Login and password-reset are different because they
 * reveal whether an *active* account exists, which registration's duplicate-check does not
 * distinguish from "just registered but unverified."
 */
@Service
public class RegistrationService {

  private static final String POLICY_VERSION = "v1";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final UserConsentRepository userConsentRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenGenerator tokenGenerator;
  private final VerificationEmailService verificationEmailService;
  private final SecurityEventLogger securityEventLogger;
  private final AuthProperties authProperties;
  private final Clock clock;

  public RegistrationService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      UserConsentRepository userConsentRepository,
      PasswordEncoder passwordEncoder,
      TokenGenerator tokenGenerator,
      VerificationEmailService verificationEmailService,
      SecurityEventLogger securityEventLogger,
      AuthProperties authProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.userConsentRepository = userConsentRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenGenerator = tokenGenerator;
    this.verificationEmailService = verificationEmailService;
    this.securityEventLogger = securityEventLogger;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  /**
   * Commits the account, role, consents, and verification token in one transaction, then sends the
   * verification email *after* that commit (brief §33) - a failed send never rolls back a
   * successful registration; the user can always request a new one via resend-verification.
   */
  public User registerAndSendVerificationEmail(
      String email,
      String rawPassword,
      String firstName,
      boolean acceptTerms,
      boolean acceptPrivacyPolicy) {
    RegisteredAccount registered =
        registerTransactionally(email, rawPassword, firstName, acceptTerms, acceptPrivacyPolicy);
    verificationEmailService.send(registered.user().getEmail(), registered.rawToken());
    return registered.user();
  }

  record RegisteredAccount(User user, String rawToken) {}

  @Transactional
  RegisteredAccount registerTransactionally(
      String rawEmail,
      String rawPassword,
      String firstName,
      boolean acceptTerms,
      boolean acceptPrivacyPolicy) {
    String email = normalizeEmail(rawEmail);
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EMAIL_ALREADY_REGISTERED",
          "An account with this email already exists");
    }

    Role userRole =
        roleRepository
            .findByCode("USER")
            .orElseThrow(
                () ->
                    new IllegalStateException("USER role missing - seed migration V6 did not run"));

    User user =
        User.newRegistration(email, passwordEncoder.encode(rawPassword), blankToNull(firstName));
    user.addRole(userRole);
    user = userRepository.save(user);

    if (acceptTerms) {
      userConsentRepository.save(
          new UserConsent(user, ConsentType.TERMS_OF_SERVICE, POLICY_VERSION, null));
    }
    if (acceptPrivacyPolicy) {
      userConsentRepository.save(
          new UserConsent(user, ConsentType.PRIVACY_POLICY, POLICY_VERSION, null));
    }

    TokenGenerator.GeneratedToken token = tokenGenerator.generate();
    Instant expiresAt = clock.instant().plus(authProperties.emailVerificationTokenTtl());
    emailVerificationTokenRepository.save(
        new EmailVerificationToken(user, token.tokenHash(), expiresAt));

    securityEventLogger.log(SecurityEventLogger.Event.USER_REGISTERED, user.getId().toString());
    return new RegisteredAccount(user, token.rawToken());
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }
}
