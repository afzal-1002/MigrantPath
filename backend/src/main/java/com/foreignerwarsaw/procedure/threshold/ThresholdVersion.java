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
  @JoinColumn(name = "submitted_by")
  private User submittedBy;

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

  public String getNotes() {
    return notes;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public User getSubmittedBy() {
    return submittedBy;
  }

  public User getApprovedBy() {
    return approvedBy;
  }

  public User getPublishedBy() {
    return publishedBy;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public long getLockVersion() {
    return lockVersion;
  }

  /** Phase 9 addition (brief §47): edit a still-DRAFT threshold version's value/dates/notes. */
  public void updateDraftContent(
      BigDecimal value, String valueText, LocalDate effectiveFrom, String notes) {
    requireMutable();
    this.value = value;
    this.valueText = valueText;
    this.effectiveFrom = effectiveFrom;
    this.notes = notes;
  }

  public void submitForReview(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.IN_REVIEW);
    this.status = PublicationStatus.IN_REVIEW;
    this.submittedBy = actor;
    this.submittedAt = at;
  }

  public void sendBackToDraft() {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.DRAFT);
    this.status = PublicationStatus.DRAFT;
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

  /**
   * Phase 9 addition (brief §46/§77) - {@code ThresholdVersion} previously had no archive
   * transition at all, unlike its three siblings; withdrawing a published threshold now works the
   * same way withdrawing a published Procedure/Rule/Questionnaire version does.
   */
  public void archive() {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.ARCHIVED);
    this.status = PublicationStatus.ARCHIVED;
  }

  public void closeEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  private void requireMutable() {
    if (status == PublicationStatus.PUBLISHED || status == PublicationStatus.ARCHIVED) {
      throw new IllegalStateException(
          "ThresholdVersion content is immutable once "
              + status
              + " - create a new draft version instead");
    }
  }
}
