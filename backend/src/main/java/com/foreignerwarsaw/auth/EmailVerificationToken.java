package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Only {@link #tokenHash} is ever persisted (brief §34) - the raw token exists in memory only long
 * enough to be emailed (see {@link TokenGenerator}) and is never logged. See
 * V3__create_auth_tokens.sql / docs/database/DATABASE.md §1.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 128)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EmailVerificationToken() {}

  public EmailVerificationToken(User user, String tokenHash, Instant expiresAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public void markUsed(Instant at) {
    this.usedAt = at;
  }
}
