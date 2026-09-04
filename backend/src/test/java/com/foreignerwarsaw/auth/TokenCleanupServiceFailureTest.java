package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.config.TokenCleanupProperties;
import com.foreignerwarsaw.observability.TokenCleanupMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Canonical Phase 14 (Observability) - the "cleanup job failure" failure exercise: a real, mocked
 * repository exception must produce both {@code token.cleanup.run} (already recorded before the
 * failure) AND {@code token.cleanup.failure}, and the exception must still propagate afterward so
 * Spring's own scheduled-task error logging (the mechanism that surfaced the Phase 13
 * self-invocation bug in the first place) still sees it. A plain Mockito unit test, not a
 * Testcontainers integration test - forcing a real Postgres-level failure mid-test would need
 * disruptive container manipulation for no extra coverage of the actual thing being verified here
 * (the try/catch/metrics wiring in {@link TokenCleanupService#scheduledCleanup()}), which is
 * exactly why the metrics themselves exist as thin, directly-testable collaborators.
 */
@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceFailureTest {

  @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private TokenCleanupProperties properties;

  private SimpleMeterRegistry meterRegistry;
  private TokenCleanupService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    TokenCleanupMetrics metrics = new TokenCleanupMetrics(meterRegistry);
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    service =
        new TokenCleanupService(
            emailVerificationTokenRepository,
            passwordResetTokenRepository,
            properties,
            metrics,
            clock);
  }

  @Test
  void aRealRepositoryFailure_recordsRunAndFailureMetrics_thenRethrows() {
    when(properties.enabled()).thenReturn(true);
    when(properties.usedTokenRetention()).thenReturn(Duration.ofDays(1));
    when(emailVerificationTokenRepository.deleteExpiredOrStale(any(), any()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("simulated DB timeout"));

    assertThatThrownBy(() -> service.scheduledCleanup())
        .isInstanceOf(org.springframework.dao.QueryTimeoutException.class);

    assertThat(meterRegistry.find("token.cleanup.run").counter().count()).isEqualTo(1.0);
    assertThat(meterRegistry.find("token.cleanup.failure").counter().count()).isEqualTo(1.0);
    // The deleted-count metric only fires from inside cleanupExpiredAndStaleTokens() itself -
    // never reached once the repository call throws, so it must stay completely unregistered
    // rather than silently reporting a false zero.
    assertThat(meterRegistry.find("token.cleanup.deleted").counter()).isNull();
    verify(passwordResetTokenRepository, never()).deleteExpiredOrStale(any(), any());
  }

  @Test
  void disabledCleanup_neverTouchesRepositoriesOrMetrics() {
    when(properties.enabled()).thenReturn(false);

    service.scheduledCleanup();

    verify(emailVerificationTokenRepository, never()).deleteExpiredOrStale(any(), any());
    assertThat(meterRegistry.find("token.cleanup.run").counter()).isNull();
  }
}
