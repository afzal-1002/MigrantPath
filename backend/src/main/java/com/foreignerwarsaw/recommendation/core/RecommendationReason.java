package com.foreignerwarsaw.recommendation.core;

import com.foreignerwarsaw.rules.core.RuleVersion;
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
 * One structured, machine-readable "why" entry for a {@link Recommendation} (brief §10/§11) -
 * {@link #reasonCode} is a stable key ({@code RecommendationReasonMapper} produces it from a Phase
 * 6 {@link com.foreignerwarsaw.rules.evaluation.ConditionTrace}), never persisted English prose
 * (brief §54). Translation happens in the frontend from {@link #messageKey}.
 */
@Entity
@Table(name = "recommendation_reasons")
public class RecommendationReason {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recommendation_id", nullable = false)
  private Recommendation recommendation;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_type", nullable = false, length = 30)
  private RecommendationReasonType reasonType;

  @Column(name = "reason_code", nullable = false, length = 100)
  private String reasonCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rule_version_id")
  private RuleVersion ruleVersion;

  @Column(name = "condition_code", length = 100)
  private String conditionCode;

  @Column(name = "fact_code", length = 100)
  private String factCode;

  @Column(name = "message_key", length = 200)
  private String messageKey;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  protected RecommendationReason() {}

  public RecommendationReason(
      Recommendation recommendation,
      RecommendationReasonType reasonType,
      String reasonCode,
      RuleVersion ruleVersion,
      String conditionCode,
      String factCode,
      String messageKey,
      int displayOrder) {
    this.recommendation = recommendation;
    this.reasonType = reasonType;
    this.reasonCode = reasonCode;
    this.ruleVersion = ruleVersion;
    this.conditionCode = conditionCode;
    this.factCode = factCode;
    this.messageKey = messageKey;
    this.displayOrder = displayOrder;
  }

  public UUID getId() {
    return id;
  }

  public Recommendation getRecommendation() {
    return recommendation;
  }

  public RecommendationReasonType getReasonType() {
    return reasonType;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public RuleVersion getRuleVersion() {
    return ruleVersion;
  }

  public String getConditionCode() {
    return conditionCode;
  }

  public String getFactCode() {
    return factCode;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
