package com.foreignerwarsaw.questionnaire.core;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Versioned questionnaire content (docs/database/DATABASE.md §4, brief §3/§4/§44). Reuses {@link
 * PublicationStatus}/{@link PublicationStateMachine} directly from the {@code procedure} package
 * rather than duplicating the lifecycle (brief §45: "if reusing Phase 4 lifecycle is clean... is
 * acceptable") - Phase 4 code is untouched by this reuse.
 *
 * <p>An {@code Assessment} binds permanently to one {@code QuestionnaireVersion.id} at creation
 * time (brief §4) - once a version has been used by any assessment, or is {@code PUBLISHED}, its
 * question structure must never be mutated in place (brief §44); a content change means drafting a
 * new version instead. This class's mutators enforce that the same way {@code ProcedureVersion}'s
 * do.
 */
@Entity
@Table(name = "questionnaire_versions")
public class QuestionnaireVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_id", nullable = false)
  private Questionnaire questionnaire;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PublicationStatus status = PublicationStatus.DRAFT;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

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

  protected QuestionnaireVersion() {}

  public static QuestionnaireVersion draft(
      Questionnaire questionnaire,
      int versionNumber,
      String title,
      String description,
      User createdBy) {
    QuestionnaireVersion version = new QuestionnaireVersion();
    version.questionnaire = questionnaire;
    version.versionNumber = versionNumber;
    version.title = title;
    version.description = description;
    version.createdBy = createdBy;
    return version;
  }

  public UUID getId() {
    return id;
  }

  public Questionnaire getQuestionnaire() {
    return questionnaire;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getTitle() {
    return title;
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

  public void updateDraftContent(String title, String description) {
    requireMutable();
    this.title = title;
    this.description = description;
  }

  public void submitForReview(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.IN_REVIEW);
    this.status = PublicationStatus.IN_REVIEW;
    this.submittedBy = actor;
    this.submittedAt = at;
  }

  /**
   * Phase 9 addition (brief §49) - {@code QuestionnaireVersion} previously had no reverse
   * transition, unlike its three siblings.
   */
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
   * QuestionnaireVersionService} owns publish-readiness validation (cycle detection, effective-date
   * bookkeeping) and the "close the previous active version" side effect, matching {@code
   * ProcedureVersion#markPublished}'s split of responsibility.
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

  public void closeEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  private void requireMutable() {
    if (status == PublicationStatus.PUBLISHED || status == PublicationStatus.ARCHIVED) {
      throw new IllegalStateException(
          "QuestionnaireVersion content is immutable once "
              + status
              + " - create a new draft version instead");
    }
  }
}
