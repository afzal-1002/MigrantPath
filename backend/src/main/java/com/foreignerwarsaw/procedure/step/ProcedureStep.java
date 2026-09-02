package com.foreignerwarsaw.procedure.step;

import com.foreignerwarsaw.procedure.core.Procedure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Stable step identity, kept separate from {@link StepVersion} (docs/database/DATABASE.md §3/§8,
 * brief §12) - a future {@code UserCaseStep} (Phase 8) needs a reference that survives wording
 * changes across procedure versions, plus a pinned {@link StepVersion} for what was actually shown.
 */
@Entity
@Table(name = "procedure_steps")
public class ProcedureStep {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  protected ProcedureStep() {}

  public ProcedureStep(Procedure procedure, String stableCode) {
    this.procedure = procedure;
    this.stableCode = stableCode;
  }

  public UUID getId() {
    return id;
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public String getStableCode() {
    return stableCode;
  }
}
