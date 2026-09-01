package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.security.SessionInvalidator;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.PasswordResetEmailService;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository tokenRepository;
  @Mock private PasswordResetEmailService passwordResetEmailService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RateLimiter rateLimiter;
  @Mock private SessionInvalidator sessionInvalidator;
  @Mock private SecurityEventLogger securityEventLogger;

  private PasswordResetService service;
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
        new PasswordResetService(
            userRepository,
            tokenRepository,
            new TokenGenerator(),
            passwordResetEmailService,
            passwordEncoder,
            rateLimiter,
            sessionInvalidator,
            securityEventLogger,
            authProperties,
            clock);
    user = User.newRegistration("person@example.com", "old-hash", null);
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
  }

  @Test
  void expiredResetTokenIsRejected() {
    PasswordResetToken token = new PasswordResetToken(user, "hash", NOW.minusSeconds(1));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.resetPassword("raw", "newPassword1"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOKEN_EXPIRED"));
  }

  @Test
  void usedResetTokenCannotBeReplayed() {
    PasswordResetToken token = new PasswordResetToken(user, "hash", NOW.plusSeconds(60));
    token.markUsed(NOW.minusSeconds(30));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> service.resetPassword("raw", "newPassword1"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOKEN_INVALID"));
  }

  @Test
  void validTokenUpdatesPasswordInvalidatesOtherTokensAndAllSessions() {
    PasswordResetToken token = new PasswordResetToken(user, "hash", NOW.plusSeconds(60));
    PasswordResetToken otherOutstanding =
        new PasswordResetToken(user, "other-hash", NOW.plusSeconds(60));
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(tokenRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of(otherOutstanding));
    when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

    service.resetPassword("raw", "newPassword1");

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(token.isUsed()).isTrue();
    assertThat(otherOutstanding.isUsed()).isTrue();
    verify(sessionInvalidator).invalidateAllSessionsFor(user.getEmail());
    verify(securityEventLogger)
        .log(SecurityEventLogger.Event.PASSWORD_RESET_COMPLETED, user.getId().toString());
  }

  @Test
  void forgotPasswordSkipsSilentlyWhenCooldownActive() {
    when(rateLimiter.tryAcquire(any(), any())).thenReturn(false);

    service.forgotPassword("person@example.com");

    verify(userRepository, org.mockito.Mockito.never()).findByEmailIgnoreCase(any());
  }
}
