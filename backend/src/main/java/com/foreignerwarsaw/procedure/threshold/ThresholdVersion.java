package com.foreignerwarsaw.procedure.threshold;

import com.foreignerwarsaw.procedure.PublicationStateMachine;
import com.foreignerwarsaw.procedure.PublicationStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Same identity+version+exclusion-constraint pattern as {@code ProcedureVersion}, and the same
 * publication lifecycle (shared {@link PublicationStateMachine}) - see {@code Threshold}'s Javadoc
 * for why this is independent of any one procedure.
 */
@Entity
@Table(name = "threshold_versions")
public class ThresholdVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "threshold_id", nullable = false)
  private Threshold threshold;

  @Column(precision = 18, scale = 4)
  private BigDecimal value;

  @Column(name = "value_text", columnDefinition = "text")
  private String valueText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PublicationStatus status = PublicationStatus.DRAFT;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(columnDefinition = "text")
  private String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by")
  private User approvedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "published_by")
  private User publishedBy;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @jakarta.persistence.Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected ThresholdVersion() {}

  public static ThresholdVersion draft(
      Threshold threshold, BigDecimal value, String valueText, User createdBy) {
    ThresholdVersion version = new ThresholdVersion();
    version.threshold = threshold;
    version.value = value;
    version.valueText = valueText;
    version.createdBy = createdBy;
    return version;
  }

  public UUID getId() {
    return id;
  }

  public Threshold getThreshold() {
    return threshold;
  }

  public BigDecimal getValue() {
    return value;
  }

  public String getValueText() {
    return valueText;
  }

  public PublicationStatus getStatus() {
    return status;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public User getPublishedBy() {
    return publishedBy;
  }

  public void submitForReview(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.IN_REVIEW);
    this.status = PublicationStatus.IN_REVIEW;
    this.submittedAt = at;
  }

  public void approve(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.APPROVED);
    this.status = PublicationStatus.APPROVED;
    this.approvedBy = actor;
    this.approvedAt = at;
  }

  public void markPublished(User actor, Instant at, LocalDate effectiveFrom) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.PUBLISHED);
    this.status = PublicationStatus.PUBLISHED;
    this.publishedBy = actor;
    this.publishedAt = at;
    this.effectiveFrom = effectiveFrom;
  }

  public void closeEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }
}
