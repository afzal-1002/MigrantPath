package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Append-only (never updated) - see V4__create_user_consents.sql. */
@Entity
@Table(name = "user_consents")
public class UserConsent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "consent_type", nullable = false, length = 30)
  private ConsentType consentType;

  @Column(name = "policy_version", nullable = false, length = 50)
  private String policyVersion;

  @CreationTimestamp
  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  protected UserConsent() {}

  public UserConsent(User user, ConsentType consentType, String policyVersion, String ipAddress) {
    this.user = user;
    this.consentType = consentType;
    this.policyVersion = policyVersion;
    this.ipAddress = ipAddress;
  }

  public UUID getId() {
    return id;
  }

  public ConsentType getConsentType() {
    return consentType;
  }

  public String getPolicyVersion() {
    return policyVersion;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }
}
