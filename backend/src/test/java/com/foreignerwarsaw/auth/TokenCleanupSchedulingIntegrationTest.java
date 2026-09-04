package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.foreignerwarsaw.AbstractIntegrationTest;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Canonical Phase 13 (Deployment) real finding: {@link TokenCleanupService#scheduledCleanup()}
 * called {@code cleanupExpiredAndStaleTokens()} as a same-class self-invocation, which bypasses the
 * Spring AOP transactional proxy entirely - the real {@code @Scheduled} trigger threw {@code
 * jakarta.persistence.TransactionRequiredException} on every run against a real deployed stack
 * (staging/production both hardcode this job {@code enabled: true}). {@link
 * TokenCleanupServiceTest} never caught this because it calls {@code
 * cleanupExpiredAndStaleTokens()} directly on the injected (proxied) bean - exactly the one call
 * shape self-invocation does NOT affect. This class exercises the actual entry point the scheduler
 * itself calls, {@code scheduledCleanup()}, proving the fix (moving {@code @Transactional} onto
 * that entry point) actually works.
 *
 * <p>A dedicated, isolated test class rather than adding this to {@code TokenCleanupServiceTest} -
 * {@code app.token-cleanup.enabled=true} here (via {@code @TestPropertySource}) makes Spring build
 * a separate cached context (a different property set), so this is the only test class where the
 * real {@code @Scheduled} background trigger is ever live - never risking a stray scheduled run
 * racing another test class's assertions against the same tables (the exact hazard this service's
 * own class Javadoc already warns about).
 */
@TestPropertySource(properties = "app.token-cleanup.enabled=true")
class TokenCleanupSchedulingIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TokenCleanupService tokenCleanupService;
  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private Clock clock;

  @Test
  void scheduledCleanup_realEntryPoint_actuallyDeletesUnderItsOwnTransaction() {
    User user = User.newRegistration(uniqueEmail(), "irrelevant-hash", "Test");
    user.markEmailVerified(Instant.now());
    user.addRole(roleRepository.findByCode("USER").orElseThrow());
    user = userRepository.save(user);

    Instant now = clock.instant();
    EmailVerificationToken expired =
        emailVerificationTokenRepository.save(
            new EmailVerificationToken(
                user, UUID.randomUUID().toString(), now.minus(Duration.ofHours(1))));

    // The real bug this test guards: calling the @Scheduled entry point itself (not the
    // inner method directly) must not throw TransactionRequiredException.
    assertThatCode(tokenCleanupService::scheduledCleanup).doesNotThrowAnyException();

    assertThat(emailVerificationTokenRepository.findById(expired.getId())).isEmpty();
  }

  private String uniqueEmail() {
    return "token-cleanup-sched-" + UUID.randomUUID() + "@example.com";
  }
}
