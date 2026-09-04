package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.config.TokenCleanupProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) token retention (brief §34/§35/§40/§149). Removes
 * expired-and-never-used and used-longer-than-{@link TokenCleanupProperties#usedTokenRetention}
 * verification/reset token rows. Idempotent (a row already deleted simply isn't matched again) and
 * safe to call directly outside the schedule (used by {@code TokenCleanupServiceTest} with a fixed
 * {@link Clock} - no {@code Thread.sleep} anywhere in this class or its tests).
 *
 * <p>{@code app.token-cleanup.enabled} defaults {@code false} in the base profile (same
 * fail-safe-by-default convention {@code AdminBootstrapRunner} established) and is explicitly
 * turned on per real environment (local/staging/production) - never implicitly on for the test
 * profile, so a scheduled run can never race a test's own assertions against these tables.
 *
 * <p><b>Horizontal-scaling implication</b> (brief §35, explicitly not solved here): this is a
 * single-instance, in-process {@code @Scheduled} job with no distributed lock. Running more than
 * one backend instance means every instance independently attempts the same cleanup on its own
 * schedule - harmless (each `DELETE ... WHERE` is naturally idempotent, and Postgres serializes the
 * concurrent statements), just redundant work, not a correctness bug. If a future phase moves to
 * multiple instances and the redundant work becomes worth avoiding, this needs a distributed
 * scheduler or a leader-election mechanism - not attempted here, matching this project's
 * "single-instance MVP" scope for the rate limiter and other in-process state.
 */
@Service
public class TokenCleanupService {

  private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final TokenCleanupProperties properties;
  private final Clock clock;

  public TokenCleanupService(
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      TokenCleanupProperties properties,
      Clock clock) {
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.token-cleanup.interval:PT1H}")
  @Transactional
  public void scheduledCleanup() {
    // Canonical Phase 13 (Deployment) real finding: this @Scheduled entry point calling
    // cleanupExpiredAndStaleTokens() below is a same-class *self-invocation* - it never
    // goes through the Spring AOP proxy, so that method's own @Transactional had no
    // effect when reached this way, only when called externally (exactly how
    // TokenCleanupServiceTest calls it, on the injected bean, which is why the test
    // never caught this). The real scheduled trigger threw
    // jakarta.persistence.TransactionRequiredException on every run in a real
    // deployed stack (staging/production both hardcode this job enabled) - found by
    // actually running the built image with the job enabled, not by unit/integration
    // tests alone. @Transactional here on the proxied entry point itself opens the
    // transaction before the self-invoked call executes, which is then correctly
    // covered by the already-bound transactional resources regardless of the inner
    // call bypassing the proxy.
    if (!properties.enabled()) {
      return;
    }
    cleanupExpiredAndStaleTokens();
  }

  @Transactional
  public void cleanupExpiredAndStaleTokens() {
    var now = clock.instant();
    var usedRetentionCutoff = now.minus(properties.usedTokenRetention());
    int verificationDeleted =
        emailVerificationTokenRepository.deleteExpiredOrStale(now, usedRetentionCutoff);
    int resetDeleted = passwordResetTokenRepository.deleteExpiredOrStale(now, usedRetentionCutoff);
    if (verificationDeleted > 0 || resetDeleted > 0) {
      // No token value, hash, or user identifier logged - only counts (docs/privacy/
      // LOGGING_PRIVACY.md).
      log.info(
          "Token cleanup removed {} verification token(s) and {} password reset token(s)",
          verificationDeleted,
          resetDeleted);
    }
  }
}
