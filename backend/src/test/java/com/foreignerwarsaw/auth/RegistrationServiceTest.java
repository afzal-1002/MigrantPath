package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.email.VerificationEmailService;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Mock private UserConsentRepository userConsentRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private VerificationEmailService verificationEmailService;
  @Mock private SecurityEventLogger securityEventLogger;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
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
        new RegistrationService(
            userRepository,
            roleRepository,
            emailVerificationTokenRepository,
            userConsentRepository,
            passwordEncoder,
            new TokenGenerator(),
            verificationEmailService,
            securityEventLogger,
            authProperties,
            clock);
  }

  @Test
  void duplicateEmailIsRejectedWithConflict() {
    when(userRepository.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.registerTransactionally(
                    "Person@Example.com", "password123", "Pat", true, true))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void emailIsNormalizedToLowercaseAndTrimmedBeforeUniquenessCheck() {
    when(userRepository.existsByEmailIgnoreCase("person@example.com")).thenReturn(false);
    when(roleRepository.findByCode("USER")).thenReturn(Optional.of(new Role("USER", "User")));
    when(passwordEncoder.encode(any())).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(RegistrationServiceTest::withGeneratedId);

    service.registerTransactionally("  Person@Example.com  ", "password123", "Pat", true, true);

    verify(userRepository).existsByEmailIgnoreCase("person@example.com");
  }

  @Test
  void successfulRegistrationHashesPasswordAssignsUserRoleAndCreatesVerificationToken() {
    when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
    Role userRole = new Role("USER", "User");
    when(roleRepository.findByCode("USER")).thenReturn(Optional.of(userRole));
    when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
    when(userRepository.save(any(User.class))).thenAnswer(RegistrationServiceTest::withGeneratedId);

    var registered =
        service.registerTransactionally("person@example.com", "password123", "Pat", true, true);

    assertThat(registered.user().getPasswordHash()).isEqualTo("hashed-password");
    assertThat(registered.user().hasRole("USER")).isTrue();
    assertThat(registered.rawToken()).isNotBlank();
    verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    // Once each for TERMS_OF_SERVICE and PRIVACY_POLICY - both flags are true above.
    verify(userConsentRepository, org.mockito.Mockito.times(2)).save(any(UserConsent.class));
  }

  /**
   * Plain construction (and a mocked {@code save()}) never goes through Hibernate's
   * {@code @GeneratedValue(strategy = GenerationType.UUID)}, which only fires on a real persist -
   * simulate that here so code paths reading {@code user.getId()} after save (e.g. security-event
   * logging) don't NPE in this mocked test.
   */
  private static User withGeneratedId(InvocationOnMock invocation) {
    User user = invocation.getArgument(0);
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    return user;
  }

  @Test
  void missingUserRoleSeedFailsLoudlyRatherThanSilentlyOmittingTheRole() {
    when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
    when(roleRepository.findByCode("USER")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.registerTransactionally(
                    "person@example.com", "password123", null, true, true))
        .isInstanceOf(IllegalStateException.class);
  }
}
