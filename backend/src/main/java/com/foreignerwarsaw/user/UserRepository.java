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
}
