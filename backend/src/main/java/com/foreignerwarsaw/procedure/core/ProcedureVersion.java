package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.PublicationStateMachine;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.reference.geography.Jurisdiction;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Versioned, source-backed legal content for one {@link Procedure} (docs/database/DATABASE.md §3,
 * ADR-007). Once {@link PublicationStatus#PUBLISHED}, content fields must never be edited directly
 * (brief §109) - changing anything requires a new draft version (see {@link
 * com.foreignerwarsaw.procedure.core.ProcedureVersionService#createDraftFrom}); this class's own
 * mutators enforce that by refusing to run once {@link #status} is {@code PUBLISHED} or {@code
 * ARCHIVED}.
 *
 * <p>{@link #lockVersion} is Hibernate's optimistic-lock counter (brief §60) - unrelated to {@link
 * #versionNumber}, the business-visible "Version 1/2/3" a user sees.
 */
@Entity
@Table(name = "procedure_versions")
public class ProcedureVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(length = 1000)
  private String summary;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PublicationStatus status = PublicationStatus.DRAFT;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "jurisdiction_id")
  private Jurisdiction jurisdiction;

  @Column(name = "change_summary", length = 1000)
  private String changeSummary;

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

  protected ProcedureVersion() {}

  public static ProcedureVersion draft(
      Procedure procedure,
      int versionNumber,
      String title,
      String summary,
      String description,
      User createdBy) {
    ProcedureVersion version = new ProcedureVersion();
    version.procedure = procedure;
    version.versionNumber = versionNumber;
    version.title = title;
    version.summary = summary;
    version.description = description;
    version.createdBy = createdBy;
    return version;
  }

  public UUID getId() {
    return id;
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getTitle() {
    return title;
  }

  public String getSummary() {
    return summary;
  }

  public String getDescription() {
    return description;
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

  public Jurisdiction getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(Jurisdiction jurisdiction) {
    requireMutable();
    this.jurisdiction = jurisdiction;
  }

  public String getChangeSummary() {
    return changeSummary;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public User getApprovedBy() {
    return approvedBy;
  }

  public User getPublishedBy() {
    return publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void updateDraftContent(
      String title, String summary, String description, LocalDate effectiveFrom) {
    requireMutable();
    this.title = title;
    this.summary = summary;
    this.description = description;
    this.effectiveFrom = effectiveFrom;
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

  /**
   * Only the mechanical transition + timestamp/actor bookkeeping - {@link
   * com.foreignerwarsaw.procedure.core.ProcedurePublishingService} owns the publish-readiness
   * validation and the "close the previous active version" side effect.
   */
  public void markPublished(User actor, Instant at, LocalDate effectiveFrom) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.PUBLISHED);
    this.status = PublicationStatus.PUBLISHED;
    this.publishedBy = actor;
    this.publishedAt = at;
    this.effectiveFrom = effectiveFrom;
  }

  public void archive() {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.ARCHIVED);
    this.status = PublicationStatus.ARCHIVED;
  }

  /**
   * Closing an already-PUBLISHED version's effective_to (superseded by a newer version, brief §110)
   * is the one field mutation still allowed on PUBLISHED content - it's history-keeping, not a
   * content edit.
   */
  public void closeEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  private void requireMutable() {
    if (status == PublicationStatus.PUBLISHED || status == PublicationStatus.ARCHIVED) {
      throw new IllegalStateException(
          "ProcedureVersion content is immutable once "
              + status
              + " - create a new draft version instead");
    }
  }
}
