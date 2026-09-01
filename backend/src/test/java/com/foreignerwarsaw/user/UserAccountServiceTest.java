package com.foreignerwarsaw.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.security.SessionInvalidator;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.dto.ChangePasswordRequest;
import com.foreignerwarsaw.user.dto.UpdateProfileRequest;
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
class UserAccountServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionInvalidator sessionInvalidator;
  @Mock private SecurityEventLogger securityEventLogger;

  private UserAccountService service;
  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service =
        new UserAccountService(
            userRepository, passwordEncoder, sessionInvalidator, securityEventLogger);
    user = User.newRegistration("person@example.com", "old-hash", "Pat");
    userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
  }

  @Test
  void changePasswordRejectsWrongCurrentPassword() {
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.changePassword(userId, new ChangePasswordRequest("wrong", "newPassword1")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
  }

  @Test
  void changePasswordUpdatesHashAndInvalidatesAllSessions() {
    when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

    service.changePassword(userId, new ChangePasswordRequest("old-password", "newPassword1"));

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    verify(sessionInvalidator).invalidateAllSessionsFor(user.getEmail());
    verify(securityEventLogger).log(SecurityEventLogger.Event.PASSWORD_CHANGED, userId.toString());
  }

  @Test
  void updateProfileOnlyTouchesProvidedFields() {
    service.updateProfile(userId, new UpdateProfileRequest(null, "pl"));

    assertThat(user.getFirstName()).isEqualTo("Pat");
    assertThat(user.getPreferredLanguage()).isEqualTo("pl");
  }
}
