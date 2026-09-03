package com.foreignerwarsaw.procedure.step;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.reference.geography.Jurisdiction;
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
import java.util.UUID;

/**
 * A full content snapshot for one {@link ProcedureStep} within one {@link ProcedureVersion}
 * (docs/database/DATABASE.md §3) - status mirrors the parent version's own lifecycle (this class
 * carries no independent status column; it's created/published/archived together with its parent).
 *
 * <p>{@link #jurisdiction} is the content-overlay hook (brief §112-114): {@code null} means
 * "inherits the parent {@link ProcedureVersion}'s own jurisdiction"; set only when this specific
 * step's content genuinely belongs to a narrower jurisdiction than the procedure as a whole.
 */
@Entity
@Table(name = "step_versions")
public class StepVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_step_id", nullable = false)
  private ProcedureStep procedureStep;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_version_id", nullable = false)
  private ProcedureVersion procedureVersion;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "detailed_instructions", columnDefinition = "text")
  private String detailedInstructions;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, length = 30)
  private StepType stepType;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean mandatory = true;

  @Column(name = "online_available")
  private Boolean onlineAvailable;

  @Column(name = "requires_appointment")
  private Boolean requiresAppointment;

  @Column(name = "expected_user_action", columnDefinition = "text")
  private String expectedUserAction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "jurisdiction_id")
  private Jurisdiction jurisdiction;

  protected StepVersion() {}

  public StepVersion(
      ProcedureStep procedureStep,
      ProcedureVersion procedureVersion,
      String title,
      String description,
      StepType stepType,
      int sortOrder,
      boolean mandatory) {
    this.procedureStep = procedureStep;
    this.procedureVersion = procedureVersion;
    this.title = title;
    this.description = description;
    this.stepType = stepType;
    this.sortOrder = sortOrder;
    this.mandatory = mandatory;
  }

  public UUID getId() {
    return id;
  }

  public ProcedureStep getProcedureStep() {
    return procedureStep;
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getDetailedInstructions() {
    return detailedInstructions;
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

  public Boolean getOnlineAvailable() {
    return onlineAvailable;
  }

  public Boolean getRequiresAppointment() {
    return requiresAppointment;
  }

  public String getExpectedUserAction() {
    return expectedUserAction;
  }

  public Jurisdiction getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(Jurisdiction jurisdiction) {
    this.jurisdiction = jurisdiction;
  }

  /**
   * Phase 9 addition (brief §21): editing a step already added to a still-DRAFT version, rather
   * than only ever adding new ones. The caller ({@code ProcedureStepService#updateStep}) is
   * responsible for confirming the parent {@link ProcedureVersion} is still DRAFT before calling
   * this - mirrors every versioned entity's own {@code requireMutable()} convention, just enforced
   * one layer up here since a step has no independent status of its own.
   */
  public void update(
      String title,
      String description,
      String detailedInstructions,
      StepType stepType,
      int sortOrder,
      boolean mandatory) {
    this.title = title;
    this.description = description;
    this.detailedInstructions = detailedInstructions;
    this.stepType = stepType;
    this.sortOrder = sortOrder;
    this.mandatory = mandatory;
  }
}
