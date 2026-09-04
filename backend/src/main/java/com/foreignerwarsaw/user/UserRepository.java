package com.foreignerwarsaw.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Case-insensitive lookup backed by the {@code users_email_lower_uq} functional index
   * (V1__create_users.sql) - Spring Data's {@code IgnoreCase} keyword compiles to {@code
   * lower(email) = lower(?1)}, which Postgres can satisfy from that index.
   */
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  /**
   * Phase 9 admin user search (brief §81) - a simple substring match, no full-text search engine.
   */
  java.util.List<User> findByEmailContainingIgnoreCase(String emailFragment);

  /**
   * Phase 11 addition (brief §24) - the one-time admin-bootstrap gate: {@link
   * com.foreignerwarsaw.config.AdminBootstrapRunner} checks this before ever creating a bootstrap
   * account, so it can never "recreate repeatedly" once a real ADMIN exists (bootstrap-created or
   * not - any ADMIN at all satisfies it).
   */
  boolean existsByRoles_Code(String code);
}
