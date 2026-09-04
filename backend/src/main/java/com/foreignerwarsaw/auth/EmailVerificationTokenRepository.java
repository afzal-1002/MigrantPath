package com.foreignerwarsaw.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  /**
   * Canonical Phase 12 token retention (brief §34/§40/§149) - removes rows that are either
   * genuinely expired (never used, past {@code expiresAt}) or were used more than {@code
   * usedRetentionCutoff} ago (a short grace window kept after use for operational/debug value,
   * never forever - brief §40's "do not keep forever"). An active, unexpired, unused token is never
   * touched by either branch.
   */
  @Modifying
  @Query(
      "DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now"
          + " OR (t.usedAt IS NOT NULL AND t.usedAt < :usedRetentionCutoff)")
  int deleteExpiredOrStale(
      @Param("now") Instant now, @Param("usedRetentionCutoff") Instant usedRetentionCutoff);
}
