package com.foreignerwarsaw.procedure.fee;

import com.foreignerwarsaw.procedure.core.Procedure;
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
 * Fee identity; {@link FeeVersion} is snapshotted per {@code ProcedureVersion} rather than
 * independently versioned - see {@link FeeVersion}'s Javadoc for why.
 */
@Entity
@Table(name = "fees")
public class Fee {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "fee_type", nullable = false, length = 30)
  private FeeType feeType;

  protected Fee() {}

  public Fee(Procedure procedure, String stableCode, FeeType feeType) {
    this.procedure = procedure;
    this.stableCode = stableCode;
    this.feeType = feeType;
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

  public FeeType getFeeType() {
    return feeType;
  }
}
