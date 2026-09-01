package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Used to invalidate every other outstanding reset token for a user once one is successfully used
   * (brief §15 - "invalidate other outstanding reset tokens").
   */
  List<PasswordResetToken> findByUserAndUsedAtIsNull(User user);
}
