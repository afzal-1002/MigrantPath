package com.foreignerwarsaw.usercase.core;

import com.foreignerwarsaw.procedure.fee.Fee;
import com.foreignerwarsaw.procedure.fee.FeeType;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
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
import java.util.UUID;

/**
 * One user-specific fee tracking item (brief §15) - manual {@link UserCaseFeeStatus} only, no
 * payment integration (brief §152). No conditional-fee concept exists in Phase 4's schema (unlike
 * documents' {@code RequirementType}), so every fee on the active {@code ProcedureVersion} is
 * snapshotted directly.
 */
@Entity
@Table(name = "user_case_fees")
public class UserCaseFee {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "snapshot_revision_id", nullable = false)
  private UserCaseSnapshotRevision snapshotRevision;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_fee_id", nullable = false)
  private Fee sourceFee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_fee_version_id", nullable = false)
  private FeeVersion sourceFeeVersion;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "fee_type", nullable = false, length = 30)
  private FeeType feeType;

  @Column(name = "amount_snapshot", nullable = false, precision = 10, scale = 2)
  private BigDecimal amountSnapshot;

  @Column(name = "currency_snapshot", nullable = false, length = 3)
  private String currencySnapshot;

  @Column(name = "description_snapshot", length = 500)
  private String descriptionSnapshot;

  @Column(name = "payment_instructions_snapshot", columnDefinition = "text")
  private String paymentInstructionsSnapshot;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserCaseFeeStatus status = UserCaseFeeStatus.NOT_PAID;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserCaseFee() {}

  public UserCaseFee(
      UserCaseSnapshotRevision snapshotRevision,
      Fee sourceFee,
      FeeVersion sourceFeeVersion,
      String stableCode,
      FeeType feeType,
      BigDecimal amountSnapshot,
      String currencySnapshot,
      String descriptionSnapshot,
      String paymentInstructionsSnapshot,
      int sortOrder,
      Instant now) {
    this.snapshotRevision = snapshotRevision;
    this.sourceFee = sourceFee;
    this.sourceFeeVersion = sourceFeeVersion;
    this.stableCode = stableCode;
    this.feeType = feeType;
    this.amountSnapshot = amountSnapshot;
    this.currencySnapshot = currencySnapshot;
    this.descriptionSnapshot = descriptionSnapshot;
    this.paymentInstructionsSnapshot = paymentInstructionsSnapshot;
    this.sortOrder = sortOrder;
    this.updatedAt = now;
  }

  public void changeStatus(UserCaseFeeStatus newStatus, Instant at) {
    this.status = newStatus;
    this.updatedAt = at;
    this.paidAt = newStatus == UserCaseFeeStatus.PAID ? at : null;
  }

  /**
   * Used only by {@code UserCaseUpgradeService} to carry a matched item's status forward into a new
   * revision (brief §33).
   */
  public void restoreStatus(UserCaseFeeStatus status, Instant paidAt, Instant at) {
    this.status = status;
    this.paidAt = paidAt;
    this.updatedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UserCaseSnapshotRevision getSnapshotRevision() {
    return snapshotRevision;
  }

  public Fee getSourceFee() {
    return sourceFee;
  }

  public FeeVersion getSourceFeeVersion() {
    return sourceFeeVersion;
  }

  public String getStableCode() {
    return stableCode;
  }

  public FeeType getFeeType() {
    return feeType;
  }

  public BigDecimal getAmountSnapshot() {
    return amountSnapshot;
  }

  public String getCurrencySnapshot() {
    return currencySnapshot;
  }

  public String getDescriptionSnapshot() {
    return descriptionSnapshot;
  }

  public String getPaymentInstructionsSnapshot() {
    return paymentInstructionsSnapshot;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public UserCaseFeeStatus getStatus() {
    return status;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
