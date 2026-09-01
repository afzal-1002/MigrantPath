package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.security.SessionInvalidator;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.PasswordResetEmailService;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final TokenGenerator tokenGenerator;
  private final PasswordResetEmailService passwordResetEmailService;
  private final PasswordEncoder passwordEncoder;
  private final RateLimiter rateLimiter;
  private final SessionInvalidator sessionInvalidator;
  private final SecurityEventLogger securityEventLogger;
  private final AuthProperties authProperties;
  private final Clock clock;

  public PasswordResetService(
      UserRepository userRepository,
      PasswordResetTokenRepository tokenRepository,
      TokenGenerator tokenGenerator,
      PasswordResetEmailService passwordResetEmailService,
      PasswordEncoder passwordEncoder,
      RateLimiter rateLimiter,
      SessionInvalidator sessionInvalidator,
      SecurityEventLogger securityEventLogger,
      AuthProperties authProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.tokenGenerator = tokenGenerator;
    this.passwordResetEmailService = passwordResetEmailService;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.sessionInvalidator = sessionInvalidator;
    this.securityEventLogger = securityEventLogger;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  /**
   * Always returns normally with no indication of whether the account exists (brief §14/§46) - the
   * controller sends the same generic message either way. A cooldown per email prevents using this
   * to flood an inbox.
   */
  @Transactional
  public void forgotPassword(String rawEmail) {
    String email = RegistrationService.normalizeEmail(rawEmail);
    if (!rateLimiter.tryAcquire(
        "forgot-password:" + email, authProperties.forgotPasswordCooldown())) {
      return;
    }
    userRepository.findByEmailIgnoreCase(email).ifPresent(this::issueResetToken);
  }

  private void issueResetToken(User user) {
    TokenGenerator.GeneratedToken token = tokenGenerator.generate();
    Instant expiresAt = clock.instant().plus(authProperties.passwordResetTokenTtl());
    tokenRepository.save(new PasswordResetToken(user, token.tokenHash(), expiresAt));
    passwordResetEmailService.send(user.getEmail(), token.rawToken());
    securityEventLogger.log(
        SecurityEventLogger.Event.PASSWORD_RESET_REQUESTED, user.getId().toString());
  }

  /**
   * Token must exist, be unexpired, and unused (brief §15). On success: update the password hash,
   * stamp {@code password_changed_at}, mark this token used, invalidate every other outstanding
   * reset token for the user (so an old, still-valid link can't be replayed after a successful
   * reset), and invalidate all of the user's active sessions - they must sign in again with the new
   * password.
   */
  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    String hash = tokenGenerator.hash(rawToken);
    PasswordResetToken token =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "Invalid reset token"));

    Instant now = clock.instant();
    if (token.isUsed()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "This reset link has already been used");
    }
    if (token.isExpired(now)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "This reset link has expired");
    }

    User user = token.getUser();
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    token.markUsed(now);

    tokenRepository.findByUserAndUsedAtIsNull(user).forEach(other -> other.markUsed(now));

    sessionInvalidator.invalidateAllSessionsFor(user.getEmail());
    securityEventLogger.log(
        SecurityEventLogger.Event.PASSWORD_RESET_COMPLETED, user.getId().toString());
  }
}
