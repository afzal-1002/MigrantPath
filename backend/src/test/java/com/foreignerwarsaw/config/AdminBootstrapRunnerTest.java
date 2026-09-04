package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Phase 11 brief §24 - the "cannot recreate repeatedly" / "no default admin/admin" guarantee,
 * tested directly against {@link AdminBootstrapRunner}'s logic (mocked repositories - no Spring
 * context needed, the bean's own {@code @ConditionalOnProperty} gating is a separate, structural
 * guarantee already visible in {@link ProductionConfigTest}'s config-file assertions).
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PasswordEncoder passwordEncoder;

  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private AdminBootstrapRunner runner(String email, String password) {
    return new AdminBootstrapRunner(
        userRepository, roleRepository, passwordEncoder, clock, email, password);
  }

  @Test
  void skipsSilentlyWhenAnAdminAlreadyExists_regardlessOfEnvVars() throws Exception {
    when(userRepository.existsByRoles_Code("ADMIN")).thenReturn(true);

    runner("new-admin@example.com", "a-strong-password").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  void skipsWhenBootstrapEmailOrPasswordIsMissing() throws Exception {
    when(userRepository.existsByRoles_Code("ADMIN")).thenReturn(false);

    runner("", "").run(null);
    runner("admin@example.com", "").run(null);
    runner("", "a-strong-password").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  void refusesAPasswordShorterThanThePolicyMinimum() throws Exception {
    when(userRepository.existsByRoles_Code("ADMIN")).thenReturn(false);

    runner("admin@example.com", "short1").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  void refusesToSilentlyGrantAdminToAnExistingNonAdminAccount() throws Exception {
    when(userRepository.existsByRoles_Code("ADMIN")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

    runner("existing@example.com", "a-strong-password").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  void createsTheAdminAccount_verifiedAndRoleAssigned_whenNoAdminExistsYet() throws Exception {
    when(userRepository.existsByRoles_Code("ADMIN")).thenReturn(false);
    // The mixed-case input, exactly as the runner passes it through to the
    // pre-existence check (it only lowercases when actually constructing the User).
    when(userRepository.existsByEmailIgnoreCase("New-Admin@Example.com")).thenReturn(false);
    Role adminRole = new Role("ADMIN", "Administrator");
    when(roleRepository.findByCode("ADMIN")).thenReturn(java.util.Optional.of(adminRole));
    when(passwordEncoder.encode("a-strong-password")).thenReturn("{bcrypt}encoded");

    runner("New-Admin@Example.com", "a-strong-password").run(null);

    org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User created = captor.getValue();
    assertThat(created.getEmail()).isEqualTo("new-admin@example.com");
    assertThat(created.getPasswordHash()).isEqualTo("{bcrypt}encoded");
    assertThat(created.isEmailVerified()).isTrue();
  }
}
