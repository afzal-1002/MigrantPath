package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.VerificationEmailService;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

  private final UserRepository userRepository;
  private final EmailVerificationTokenRepository tokenRepository;
  private final TokenGenerator tokenGenerator;
  private final VerificationEmailService verificationEmailService;
  private final RateLimiter rateLimiter;
  private final SecurityEventLogger securityEventLogger;
  private final AuthProperties authProperties;
  private final Clock clock;

  public EmailVerificationService(
      UserRepository userRepository,
      EmailVerificationTokenRepository tokenRepository,
      TokenGenerator tokenGenerator,
      VerificationEmailService verificationEmailService,
      RateLimiter rateLimiter,
      SecurityEventLogger securityEventLogger,
      AuthProperties authProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.tokenGenerator = tokenGenerator;
    this.verificationEmailService = verificationEmailService;
    this.rateLimiter = rateLimiter;
    this.securityEventLogger = securityEventLogger;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  /**
   * Token must exist, be unexpired, and unused (brief §8) - each condition gets a distinct, honest
   * error code since (unlike login) there is no account-enumeration concern on a token the user
   * already holds from their own email.
   */
  @Transactional
  public void verify(String rawToken) {
    String hash = tokenGenerator.hash(rawToken);
    EmailVerificationToken token =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "Invalid verification token"));

    Instant now = clock.instant();
    if (token.isUsed()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "This verification link has already been used");
    }
    if (token.isExpired(now)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "This verification link has expired");
    }

    User user = token.getUser();
    user.markEmailVerified(now);
    token.markUsed(now);
    securityEventLogger.log(SecurityEventLogger.Event.EMAIL_VERIFIED, user.getId().toString());
  }

  /**
   * Always returns normally (the controller always sends the same generic response) regardless of
   * whether the email belongs to a real, unverified account - brief §8/§46. A cooldown (brief §22)
   * applies per email address so this can't be used to flood an inbox.
   */
  @Transactional
  public void resend(String rawEmail) {
    String email = RegistrationService.normalizeEmail(rawEmail);
    if (!rateLimiter.tryAcquire(
        "resend-verification:" + email, authProperties.resendVerificationCooldown())) {
      return;
    }
    userRepository
        .findByEmailIgnoreCase(email)
        .filter(user -> !user.isEmailVerified())
        .ifPresent(this::issueAndSendNewToken);
  }

  private void issueAndSendNewToken(User user) {
    TokenGenerator.GeneratedToken token = tokenGenerator.generate();
    Instant expiresAt = clock.instant().plus(authProperties.emailVerificationTokenTtl());
    tokenRepository.save(new EmailVerificationToken(user, token.tokenHash(), expiresAt));
    verificationEmailService.send(user.getEmail(), token.rawToken());
  }

  /**
   * Exposed for tests that need to assert cooldown behavior without depending on {@link Duration}
   * arithmetic living only inside the rate limiter.
   */
  Duration resendCooldown() {
    return authProperties.resendVerificationCooldown();
  }
}
