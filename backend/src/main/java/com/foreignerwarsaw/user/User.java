package com.foreignerwarsaw.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * See docs/database/DATABASE.md §1 and V1__create_users.sql. Email uniqueness is enforced by a
 * functional index on {@code lower(email)} at the database level (never trust the application layer
 * alone, brief §47) - {@link UserRepository#findByEmailIgnoreCase} relies on it.
 *
 * <p>Roles are modeled as a plain {@code @ManyToMany} against {@code user_roles} rather than a
 * dedicated join entity: the join row's only extra column ({@code granted_at}) is a
 * database-default timestamp nothing in Phase 2 reads back through JPA, so a full
 * identity+version-style join entity would be pure ceremony here. If a later phase needs to know
 * *who* granted a role, promote this to a real entity then.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "preferred_language", length = 10)
  private String preferredLanguage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserStatus status = UserStatus.PENDING_VERIFICATION;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "password_changed_at")
  private Instant passwordChangedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // No cascade: Role rows are seeded independently (V6__seed_roles.sql) and always
  // already exist by the time a User references one - cascading PERSIST here made
  // Hibernate try to re-persist an already-managed Role with an assigned id, which
  // fails as "detached entity passed to persist." Only the join-table row should be
  // written when a User's roles change, never the Role itself.
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  protected User() {}

  public static User newRegistration(
      String normalizedEmail, String passwordHash, String firstName) {
    User user = new User();
    user.email = normalizedEmail;
    user.passwordHash = passwordHash;
    user.firstName = firstName;
    user.status = UserStatus.PENDING_VERIFICATION;
    return user;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
    this.passwordChangedAt = Instant.now();
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getPreferredLanguage() {
    return preferredLanguage;
  }

  public void setPreferredLanguage(String preferredLanguage) {
    this.preferredLanguage = preferredLanguage;
  }

  public UserStatus getStatus() {
    return status;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void markEmailVerified(Instant at) {
    this.emailVerified = true;
    this.emailVerifiedAt = at;
    if (this.status == UserStatus.PENDING_VERIFICATION) {
      this.status = UserStatus.ACTIVE;
    }
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public void incrementFailedLoginAttempts() {
    this.failedLoginAttempts++;
  }

  public void resetFailedLoginAttempts() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void lockUntil(Instant until) {
    this.lockedUntil = until;
    this.status = UserStatus.LOCKED;
  }

  /**
   * Unlocks the account (e.g. once {@code lockedUntil} has passed) without touching the failure
   * count.
   */
  public void unlock() {
    this.lockedUntil = null;
    if (this.status == UserStatus.LOCKED) {
      this.status = emailVerified ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
    }
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  /**
   * Reaching this method at all means Spring Security's pre-authentication checks already accepted
   * the account (not locked - possibly because a prior lock's {@code lockedUntil} has simply passed
   * - and enabled), so it's always correct to clear lock state and, if the persisted status was
   * still {@code LOCKED}, flip it back to {@code ACTIVE} here rather than waiting on a background
   * job.
   */
  public void recordSuccessfulLogin(Instant at) {
    this.lastLoginAt = at;
    resetFailedLoginAttempts();
    if (this.status == UserStatus.LOCKED) {
      this.status = UserStatus.ACTIVE;
    }
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void addRole(Role role) {
    this.roles.add(role);
  }

  public boolean hasRole(String code) {
    return roles.stream().anyMatch(r -> r.getCode().equals(code));
  }
}
