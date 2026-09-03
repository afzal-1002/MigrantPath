package com.foreignerwarsaw.procedure.source;

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

/**
 * The append-only log behind {@link OfficialSource#getLastVerifiedAt()} (brief §24) - no automated
 * crawling exists yet; every row here is a human recording "I checked this source on this date and
 * it says X." {@link #previousVerificationId} lets a future admin UI show "what changed since the
 * last check" without a separate diff table.
 */
@Entity
@Table(name = "source_verifications")
public class SourceVerification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "official_source_id", nullable = false)
  private OfficialSource officialSource;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "checked_by")
  private User checkedBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VerificationStatus status;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "observed_hash", length = 128)
  private String observedHash;

  @Column(name = "change_detected", nullable = false)
  private boolean changeDetected = false;

  @Column(name = "previous_verification_id")
  private UUID previousVerificationId;

  protected SourceVerification() {}

  public SourceVerification(
      OfficialSource officialSource,
      Instant checkedAt,
      User checkedBy,
      VerificationStatus status,
      String notes) {
    this.officialSource = officialSource;
    this.checkedAt = checkedAt;
    this.checkedBy = checkedBy;
    this.status = status;
    this.notes = notes;
  }

  public UUID getId() {
    return id;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }

  public User getCheckedBy() {
    return checkedBy;
  }

  public VerificationStatus getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public boolean isChangeDetected() {
    return changeDetected;
  }
}
