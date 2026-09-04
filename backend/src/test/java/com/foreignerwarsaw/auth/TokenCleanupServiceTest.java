package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.config.TokenCleanupProperties;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) token retention (brief §34/§40/§149) - real
 * Testcontainers Postgres, no {@code Thread.sleep}: every expiry/retention boundary is computed
 * relative to the injected {@link Clock}'s real current instant, not a frozen one (matching this
 * codebase's existing pattern for boundary tests - e.g. {@code
 * CountryGroupMembershipRepositoryTest} - rather than needing a mutable test clock that does not
 * otherwise exist in this codebase).
 */
class TokenCleanupServiceTest extends AbstractIntegrationTest {

  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private TokenCleanupService tokenCleanupService;
  @Autowired private TokenCleanupProperties properties;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private Clock clock;

  @Test
  void cleanup_removesExpiredAndStaleUsedTokens_keepsActiveOnes() {
    User user = userWithRole();
    Instant now = clock.instant();

    // Never used, past expiry - removed.
    EmailVerificationToken expiredUnused =
        emailVerificationTokenRepository.save(
            new EmailVerificationToken(user, uniqueHash(), now.minus(Duration.ofHours(1))));

    // Used, but longer ago than the configured retention window - removed.
    EmailVerificationToken usedStale =
        emailVerificationTokenRepository.save(
            new EmailVerificationToken(user, uniqueHash(), now.plus(Duration.ofDays(7))));
    usedStale.markUsed(now.minus(properties.usedTokenRetention()).minus(Duration.ofMinutes(1)));
    emailVerificationTokenRepository.save(usedStale);

    // Used recently (within the retention window) - kept.
    EmailVerificationToken usedRecent =
        emailVerificationTokenRepository.save(
            new EmailVerificationToken(user, uniqueHash(), now.plus(Duration.ofDays(7))));
    usedRecent.markUsed(now.minus(Duration.ofMinutes(1)));
    emailVerificationTokenRepository.save(usedRecent);

    // Still active/unused/unexpired - kept.
    EmailVerificationToken active =
        emailVerificationTokenRepository.save(
            new EmailVerificationToken(user, uniqueHash(), now.plus(Duration.ofDays(1))));

    // Same three-way split for password reset tokens.
    PasswordResetToken expiredResetUnused =
        passwordResetTokenRepository.save(
            new PasswordResetToken(user, uniqueHash(), now.minus(Duration.ofHours(1))));
    PasswordResetToken activeReset =
        passwordResetTokenRepository.save(
            new PasswordResetToken(user, uniqueHash(), now.plus(Duration.ofHours(1))));

    tokenCleanupService.cleanupExpiredAndStaleTokens();

    assertThat(emailVerificationTokenRepository.findById(expiredUnused.getId())).isEmpty();
    assertThat(emailVerificationTokenRepository.findById(usedStale.getId())).isEmpty();
    assertThat(emailVerificationTokenRepository.findById(usedRecent.getId())).isPresent();
    assertThat(emailVerificationTokenRepository.findById(active.getId())).isPresent();
    assertThat(passwordResetTokenRepository.findById(expiredResetUnused.getId())).isEmpty();
    assertThat(passwordResetTokenRepository.findById(activeReset.getId())).isPresent();
  }

  @Test
  void cleanup_isIdempotent_secondRunFindsNothingLeftToDelete() {
    User user = userWithRole();
    Instant now = clock.instant();
    emailVerificationTokenRepository.save(
        new EmailVerificationToken(user, uniqueHash(), now.minus(Duration.ofHours(1))));

    tokenCleanupService.cleanupExpiredAndStaleTokens();
    // A second run must not error and must simply find nothing more to remove.
    tokenCleanupService.cleanupExpiredAndStaleTokens();
  }

  private String uniqueHash() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private User userWithRole() {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    user.markEmailVerified(Instant.now());
    user.addRole(roleRepository.findByCode("USER").orElseThrow());
    return userRepository.save(user);
  }

  private String uniqueEmail() {
    return "token-cleanup-" + UUID.randomUUID() + "@example.com";
  }
}
