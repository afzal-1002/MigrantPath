package com.foreignerwarsaw.procedure.fee;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshotted per {@link ProcedureVersion} rather than independently versioned (brief §20's "option
 * B") - matches DATABASE.md's original design, and makes snapshot-readiness (brief §49) fall out
 * for free: "the fee that applied on date X" is just "the FeeVersion belonging to the
 * ProcedureVersion active on date X," with no separate temporal system or exclusion constraint
 * needed. Amount is {@link BigDecimal} (never float/double for money, brief §104).
 */
@Entity
@Table(name = "fee_versions")
public class FeeVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fee_id", nullable = false)
  private Fee fee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_version_id", nullable = false)
  private ProcedureVersion procedureVersion;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(length = 500)
  private String description;

  @Column(name = "payment_instructions", columnDefinition = "text")
  private String paymentInstructions;

  private Boolean refundable;

  protected FeeVersion() {}

  public FeeVersion(
      Fee fee, ProcedureVersion procedureVersion, BigDecimal amount, String currency) {
    this.fee = fee;
    this.procedureVersion = procedureVersion;
    this.amount = amount;
    this.currency = currency;
  }

  public UUID getId() {
    return id;
  }

  public Fee getFee() {
    return fee;
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPaymentInstructions() {
    return paymentInstructions;
  }

  public void setPaymentInstructions(String paymentInstructions) {
    this.paymentInstructions = paymentInstructions;
  }

  public Boolean getRefundable() {
    return refundable;
  }

  public void setRefundable(Boolean refundable) {
    this.refundable = refundable;
  }
}
