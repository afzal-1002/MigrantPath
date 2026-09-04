package com.foreignerwarsaw.config;

import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production admin-bootstrap (Phase 11 brief §24) - the one safe way to create the first {@code
 * ADMIN} account in a freshly-deployed environment. Before this, the only way to grant {@code
 * ADMIN} anywhere, including production, was a raw SQL insert (the same {@code docker exec psql}
 * pattern this codebase's own Playwright tests use to self-escalate a test account, since no public
 * API can do it) - a real gap this closes.
 *
 * <p><b>Never a default {@code admin/admin}.</b> This bean only exists at all when {@code
 * app.admin-bootstrap.enabled=true} is explicitly set (never the case in {@code local}/{@code
 * test}/{@code staging}/{@code production}'s own committed profile files - an operator opts in for
 * exactly one deployment/startup, typically via a one-shot environment variable at first launch,
 * then unsets it). Even then, it does nothing unless {@code ADMIN_BOOTSTRAP_EMAIL}/{@code
 * ADMIN_BOOTSTRAP_PASSWORD} are both supplied, and - the actual "cannot recreate repeatedly"
 * guarantee - it always checks {@link UserRepository#existsByRoles_Code(String)} first: once any
 * {@code ADMIN} account exists (bootstrap-created or not), every subsequent startup is a silent
 * no-op regardless of whether the bootstrap env vars are still set. See
 * docs/operations/DEPLOYMENT.md for the exact operational procedure.
 *
 * <p>The password is never logged, at any level - only the resulting email and the fact that
 * bootstrap ran.
 */
@Component
@ConditionalOnProperty(name = "app.admin-bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final String bootstrapEmail;
  private final String bootstrapPassword;

  public AdminBootstrapRunner(
      UserRepository userRepository,
      RoleRepository roleRepository,
      PasswordEncoder passwordEncoder,
      Clock clock,
      @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String bootstrapEmail,
      @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String bootstrapPassword) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.bootstrapEmail = bootstrapEmail;
    this.bootstrapPassword = bootstrapPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRoles_Code("ADMIN")) {
      log.info(
          "Admin bootstrap: an ADMIN account already exists - skipping (this is expected on every startup after the first).");
      return;
    }
    if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
      log.warn(
          "Admin bootstrap is enabled (app.admin-bootstrap.enabled=true) but ADMIN_BOOTSTRAP_EMAIL"
              + " and/or ADMIN_BOOTSTRAP_PASSWORD is not set - no admin account was created. Set"
              + " both environment variables and restart to bootstrap the first admin.");
      return;
    }
    if (bootstrapPassword.length() < 10) {
      // Mirrors RegisterRequest's own minimum (brief §6) - never accept a weaker
      // password for the single highest-privilege account in the system.
      log.error(
          "Admin bootstrap: ADMIN_BOOTSTRAP_PASSWORD is shorter than the minimum policy length (10) -"
              + " refusing to create the account. Fix the value and restart.");
      return;
    }
    if (userRepository.existsByEmailIgnoreCase(bootstrapEmail)) {
      log.error(
          "Admin bootstrap: an account already exists for ADMIN_BOOTSTRAP_EMAIL, but it has no"
              + " ADMIN role - refusing to silently grant one. Grant the role explicitly through"
              + " the Admin panel/database if this is intentional.");
      return;
    }

    Role adminRole =
        roleRepository
            .findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
    User admin =
        User.newRegistration(
            bootstrapEmail.trim().toLowerCase(),
            passwordEncoder.encode(bootstrapPassword),
            "Admin");
    admin.markEmailVerified(Instant.now(clock));
    admin.addRole(adminRole);
    userRepository.save(admin);

    log.info(
        "Admin bootstrap: created the first ADMIN account for {} - sign in and, for defense in"
            + " depth, unset ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD (this runner is now a"
            + " permanent no-op regardless, since an ADMIN exists).",
        admin.getEmail());
  }
}
