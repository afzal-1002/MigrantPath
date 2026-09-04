package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /**
   * Used to invalidate every other outstanding reset token for a user once one is successfully used
   * (brief §15 - "invalidate other outstanding reset tokens").
   */
  List<PasswordResetToken> findByUserAndUsedAtIsNull(User user);

  /** Canonical Phase 12 token retention - see {@code EmailVerificationTokenRepository}'s twin. */
  @Modifying
  @Query(
      "DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now"
          + " OR (t.usedAt IS NOT NULL AND t.usedAt < :usedRetentionCutoff)")
  int deleteExpiredOrStale(
      @Param("now") Instant now, @Param("usedRetentionCutoff") Instant usedRetentionCutoff);
}
