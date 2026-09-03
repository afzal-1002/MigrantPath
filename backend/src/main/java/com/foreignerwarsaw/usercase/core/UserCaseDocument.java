package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.procedure.document.DocumentRequirement;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.document.RequirementType;
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
 * One user-specific document checklist item (brief §11). {@link #applicability} is structural
 * (derived once, at snapshot time, from {@link DocumentRequirementVersion#getRequirementType()} -
 * {@code DEFAULT_REQUIRED}/{@code INFORMATIONAL} -&gt; {@code APPLICABLE}, {@code CONDITIONAL}
 * -&gt; {@code NEEDS_CONFIRMATION}, never {@code NOT_APPLICABLE} - see {@link
 * UserCaseDocumentApplicability}'s Javadoc for why); {@link #status} is the user's own checklist
 * progress (brief §14) - the two are deliberately independent columns.
 */
@Entity
@Table(name = "user_case_documents")
public class UserCaseDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snapshot_revision_id", nullable = false)
  private UserCaseSnapshotRevision snapshotRevision;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_document_requirement_id", nullable = false)
  private DocumentRequirement sourceDocumentRequirement;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_document_requirement_version_id", nullable = false)
  private DocumentRequirementVersion sourceDocumentRequirementVersion;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  @Column(name = "name_snapshot", nullable = false, length = 300)
  private String nameSnapshot;

  @Column(name = "description_snapshot", columnDefinition = "text")
  private String descriptionSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_type", nullable = false, length = 20)
  private RequirementType requirementType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserCaseDocumentApplicability applicability;

  @Column(nullable = false)
  private boolean mandatory;

  @Column(name = "number_of_copies_snapshot")
  private Integer numberOfCopiesSnapshot;

  @Column(name = "original_required_snapshot")
  private Boolean originalRequiredSnapshot;

  @Column(name = "translation_required_snapshot")
  private Boolean translationRequiredSnapshot;

  @Column(name = "sworn_translation_required_snapshot")
  private Boolean swornTranslationRequiredSnapshot;

  @Column(name = "apostille_required_snapshot")
  private Boolean apostilleRequiredSnapshot;

  @Column(name = "legalisation_required_snapshot")
  private Boolean legalisationRequiredSnapshot;

  @Column(name = "validity_period_description_snapshot", length = 300)
  private String validityPeriodDescriptionSnapshot;

  @Column(name = "content_notes_snapshot", columnDefinition = "text")
  private String contentNotesSnapshot;

  @Column(name = "user_note", length = 1000)
  private String userNote;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserCaseDocumentStatus status = UserCaseDocumentStatus.NOT_STARTED;

  @Column(name = "ready_at")
  private Instant readyAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserCaseDocument() {}

  public UserCaseDocument(
      UserCaseSnapshotRevision snapshotRevision,
      DocumentRequirement sourceDocumentRequirement,
      DocumentRequirementVersion sourceDocumentRequirementVersion,
      String stableCode,
      String nameSnapshot,
      String descriptionSnapshot,
      RequirementType requirementType,
      UserCaseDocumentApplicability applicability,
      boolean mandatory,
      Integer numberOfCopiesSnapshot,
      Boolean originalRequiredSnapshot,
      Boolean translationRequiredSnapshot,
      Boolean swornTranslationRequiredSnapshot,
      Boolean apostilleRequiredSnapshot,
      Boolean legalisationRequiredSnapshot,
      String validityPeriodDescriptionSnapshot,
      String contentNotesSnapshot,
      int sortOrder,
      Instant now) {
    this.snapshotRevision = snapshotRevision;
    this.sourceDocumentRequirement = sourceDocumentRequirement;
    this.sourceDocumentRequirementVersion = sourceDocumentRequirementVersion;
    this.stableCode = stableCode;
    this.nameSnapshot = nameSnapshot;
    this.descriptionSnapshot = descriptionSnapshot;
    this.requirementType = requirementType;
    this.applicability = applicability;
    this.mandatory = mandatory;
    this.numberOfCopiesSnapshot = numberOfCopiesSnapshot;
    this.originalRequiredSnapshot = originalRequiredSnapshot;
    this.translationRequiredSnapshot = translationRequiredSnapshot;
    this.swornTranslationRequiredSnapshot = swornTranslationRequiredSnapshot;
    this.apostilleRequiredSnapshot = apostilleRequiredSnapshot;
    this.legalisationRequiredSnapshot = legalisationRequiredSnapshot;
    this.validityPeriodDescriptionSnapshot = validityPeriodDescriptionSnapshot;
    this.contentNotesSnapshot = contentNotesSnapshot;
    this.sortOrder = sortOrder;
    this.updatedAt = now;
    this.status =
        applicability == UserCaseDocumentApplicability.NOT_APPLICABLE
            ? UserCaseDocumentStatus.NOT_APPLICABLE
            : UserCaseDocumentStatus.NOT_STARTED;
  }

  public void changeStatus(UserCaseDocumentStatus newStatus, Instant at) {
    UserCaseItemTransitions.requireAllowedDocument(this.status, newStatus);
    this.status = newStatus;
    this.updatedAt = at;
    this.readyAt = newStatus == UserCaseDocumentStatus.READY ? at : null;
  }

  public void setUserNote(String userNote, Instant at) {
    this.userNote = userNote;
    this.updatedAt = at;
  }

  /**
   * Used only by the upgrade merge (brief §36) - a previously-READY document whose snapshot changed
   * materially is demoted, never silently left READY.
   */
  public void markNeedsUpdate(Instant at) {
    this.status = UserCaseDocumentStatus.NEEDS_UPDATE;
    this.updatedAt = at;
  }

  /**
   * Used only by {@code UserCaseUpgradeService} to carry a matched, unchanged item's progress
   * forward into a new revision (brief §33) - bypasses {@link UserCaseItemTransitions}, since this
   * restores prior state rather than applying a new user-submitted transition.
   */
  public void restoreStatus(UserCaseDocumentStatus status, Instant readyAt, Instant at) {
    this.status = status;
    this.readyAt = readyAt;
    this.updatedAt = at;
  }

  /**
   * Carries the user's own free-text note forward across an upgrade (brief §33's "preserve progress
   * where possible" applied to notes too).
   */
  public void restoreUserNote(String userNote) {
    this.userNote = userNote;
  }

  public UUID getId() {
    return id;
  }

  public UserCaseSnapshotRevision getSnapshotRevision() {
    return snapshotRevision;
  }

  public DocumentRequirement getSourceDocumentRequirement() {
    return sourceDocumentRequirement;
  }

  public DocumentRequirementVersion getSourceDocumentRequirementVersion() {
    return sourceDocumentRequirementVersion;
  }

  public String getStableCode() {
    return stableCode;
  }

  public String getNameSnapshot() {
    return nameSnapshot;
  }

  public String getDescriptionSnapshot() {
    return descriptionSnapshot;
  }

  public RequirementType getRequirementType() {
    return requirementType;
  }

  public UserCaseDocumentApplicability getApplicability() {
    return applicability;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public Integer getNumberOfCopiesSnapshot() {
    return numberOfCopiesSnapshot;
  }

  public Boolean getOriginalRequiredSnapshot() {
    return originalRequiredSnapshot;
  }

  public Boolean getTranslationRequiredSnapshot() {
    return translationRequiredSnapshot;
  }

  public Boolean getSwornTranslationRequiredSnapshot() {
    return swornTranslationRequiredSnapshot;
  }

  public Boolean getApostilleRequiredSnapshot() {
    return apostilleRequiredSnapshot;
  }

  public Boolean getLegalisationRequiredSnapshot() {
    return legalisationRequiredSnapshot;
  }

  public String getValidityPeriodDescriptionSnapshot() {
    return validityPeriodDescriptionSnapshot;
  }

  public String getContentNotesSnapshot() {
    return contentNotesSnapshot;
  }

  public String getUserNote() {
    return userNote;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public UserCaseDocumentStatus getStatus() {
    return status;
  }

  public Instant getReadyAt() {
    return readyAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
