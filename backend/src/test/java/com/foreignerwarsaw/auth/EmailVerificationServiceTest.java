package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.VerificationEmailService;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises the token-lifecycle rules (valid / expired / used / unknown) entirely with mocks and a
 * fixed {@link Clock} - brief §37/§36.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationTokenRepository tokenRepository;
  @Mock private VerificationEmailService verificationEmailService;
  @Mock private RateLimiter rateLimiter;
  @Mock private SecurityEventLogger securityEventLogger;

  private EmailVerificationService service;
  private User user;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    AuthProperties authProperties =
        new AuthProperties(
            "http://localhost:4200",
            Duration.ofHours(24),
            Duration.ofMinutes(30),
            5,
            Duration.ofMinutes(15),
            Duration.ofMinutes(1),
            Duration.ofMinutes(1));
    service =
        new EmailVerificationService(
            userRepository,
            tokenRepository,
            new TokenGenerator(),
            verificationEmailService,
            rateLimiter,
            securityEventLogger,
            authProperties,
            clock);
    user = User.newRegistration("person@example.com", "hash", null);
    // Plain construction never goes through JPA persist, so @GeneratedValue never
    // fires - the id has to be set for tests that touch code paths reading it.
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
  }

  @Test
  void validUnexpiredUnusedTokenActivatesTheAccount() {
    EmailVerificationToken token = new EmailVerificationToken(user, "hash", NOW.plusSeconds(60));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    service.verify("raw-token");

    assertThat(user.isEmailVerified()).isTrue();
    assertThat(token.isUsed()).isTrue();
    verify(securityEventLogger)
        .log(SecurityEventLogger.Event.EMAIL_VERIFIED, user.getId().toString());
  }

  @Test
  void expiredTokenIsRejectedWithTokenExpired() {
    EmailVerificationToken token = new EmailVerificationToken(user, "hash", NOW.minusSeconds(1));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.verify("raw-token"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOKEN_EXPIRED"));
    assertThat(user.isEmailVerified()).isFalse();
  }

  @Test
  void alreadyUsedTokenIsRejectedWithTokenInvalid() {
    EmailVerificationToken token = new EmailVerificationToken(user, "hash", NOW.plusSeconds(60));
    token.markUsed(NOW.minusSeconds(30));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.verify("raw-token"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOKEN_INVALID"));
  }

  @Test
  void unknownTokenIsRejectedWithTokenInvalid() {
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify("does-not-exist"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOKEN_INVALID"));
  }

  @Test
  void resendIsSkippedWhenCooldownNotElapsed() {
    when(rateLimiter.tryAcquire(any(), any())).thenReturn(false);

    service.resend("person@example.com");

    verifyNoInteractions(userRepository, verificationEmailService);
  }

  @Test
  void resendDoesNothingForAlreadyVerifiedAccounts() {
    when(rateLimiter.tryAcquire(any(), any())).thenReturn(true);
    user.markEmailVerified(NOW);
    when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));

    service.resend("person@example.com");

    verifyNoInteractions(verificationEmailService);
  }
}
