package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.procedure.step.ProcedureStep;
import com.foreignerwarsaw.procedure.step.StepType;
import com.foreignerwarsaw.procedure.step.StepVersion;
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
 * One user-specific step checklist item (brief §9) - never mutates {@link ProcedureStep}/{@link
 * StepVersion}. {@link #sourceProcedureStep} is the stable identity {@code UserCaseUpgradeService}
 * matches on across revisions (brief §29/§34); {@link #sourceStepVersion} pins exactly which
 * content was shown.
 *
 * <p>No conditional-step engine exists yet (brief §10/§72 - Phase 6 does not resolve a {@code
 * STEP}-target rule to a specific step identity, see docs/rules/RULE_SCHEMA.md's target-type
 * discussion) - every step from the active {@code ProcedureVersion} is snapshotted as applicable,
 * starting {@code NOT_STARTED}. {@code NOT_APPLICABLE}/{@code BLOCKED} are reserved for that future
 * engine, never set by Phase 8 itself.
 */
@Entity
@Table(name = "user_case_steps")
public class UserCaseStep {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snapshot_revision_id", nullable = false)
  private UserCaseSnapshotRevision snapshotRevision;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_procedure_step_id", nullable = false)
  private ProcedureStep sourceProcedureStep;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_step_version_id", nullable = false)
  private StepVersion sourceStepVersion;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  @Column(name = "title_snapshot", nullable = false, length = 300)
  private String titleSnapshot;

  @Column(name = "description_snapshot", columnDefinition = "text")
  private String descriptionSnapshot;

  @Column(name = "detailed_instructions_snapshot", columnDefinition = "text")
  private String detailedInstructionsSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, length = 30)
  private StepType stepType;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean mandatory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserCaseStepStatus status = UserCaseStepStatus.NOT_STARTED;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserCaseStep() {}

  public UserCaseStep(
      UserCaseSnapshotRevision snapshotRevision,
      ProcedureStep sourceProcedureStep,
      StepVersion sourceStepVersion,
      String stableCode,
      String titleSnapshot,
      String descriptionSnapshot,
      String detailedInstructionsSnapshot,
      StepType stepType,
      int sortOrder,
      boolean mandatory,
      Instant now) {
    this.snapshotRevision = snapshotRevision;
    this.sourceProcedureStep = sourceProcedureStep;
    this.sourceStepVersion = sourceStepVersion;
    this.stableCode = stableCode;
    this.titleSnapshot = titleSnapshot;
    this.descriptionSnapshot = descriptionSnapshot;
    this.detailedInstructionsSnapshot = detailedInstructionsSnapshot;
    this.stepType = stepType;
    this.sortOrder = sortOrder;
    this.mandatory = mandatory;
    this.updatedAt = now;
  }

  public void changeStatus(UserCaseStepStatus newStatus, Instant at) {
    UserCaseItemTransitions.requireAllowedStep(this.status, newStatus);
    this.status = newStatus;
    this.updatedAt = at;
    this.completedAt = newStatus == UserCaseStepStatus.COMPLETED ? at : null;
  }

  /**
   * Used only by {@code UserCaseUpgradeService} to carry a matched item's progress forward into a
   * new revision (brief §34) - deliberately bypasses {@link UserCaseItemTransitions}, since this
   * restores prior state rather than applying a new user-submitted transition.
   */
  public void restoreStatus(UserCaseStepStatus status, Instant completedAt, Instant at) {
    this.status = status;
    this.completedAt = completedAt;
    this.updatedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UserCaseSnapshotRevision getSnapshotRevision() {
    return snapshotRevision;
  }

  public ProcedureStep getSourceProcedureStep() {
    return sourceProcedureStep;
  }

  public StepVersion getSourceStepVersion() {
    return sourceStepVersion;
  }

  public String getStableCode() {
    return stableCode;
  }

  public String getTitleSnapshot() {
    return titleSnapshot;
  }

  public String getDescriptionSnapshot() {
    return descriptionSnapshot;
  }

  public String getDetailedInstructionsSnapshot() {
    return detailedInstructionsSnapshot;
  }

  public StepType getStepType() {
    return stepType;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public UserCaseStepStatus getStatus() {
    return status;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
