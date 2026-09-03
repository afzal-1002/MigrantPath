package com.foreignerwarsaw.rules.core;

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
 * Stable rule identity (docs/database/DATABASE.md §5, ADR-009) - answers only "what legal question
 * is this rule, and what does it target," never "does a given user satisfy it" (that's {@link
 * RuleVersion}'s condition tree, evaluated by {@code
 * com.foreignerwarsaw.rules.evaluation.RuleEvaluator}). Mirrors {@link
 * com.foreignerwarsaw.procedure.core.Procedure}'s and {@link
 * com.foreignerwarsaw.procedure.threshold.Threshold}'s identity+version split.
 *
 * <p>{@link #targetCode} is a stable business code (e.g. a {@code Procedure.code}), never a version
 * id (brief §61/§62) - {@code Rule} and its target evolve on independent version timelines; {@code
 * evaluationDate} ties them together only at evaluation time.
 */
@Entity
@Table(name = "rules")
public class Rule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_type", nullable = false, length = 30)
  private RuleType ruleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 30)
  private RuleTargetType targetType;

  @Column(name = "target_code", nullable = false, length = 50)
  private String targetCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "jurisdiction_id")
  private Jurisdiction jurisdiction;

  @Column(nullable = false)
  private boolean active = true;

  protected Rule() {}

  public Rule(
      String code,
      String canonicalName,
      RuleType ruleType,
      RuleTargetType targetType,
      String targetCode) {
    this.code = code;
    this.canonicalName = canonicalName;
    this.ruleType = ruleType;
    this.targetType = targetType;
    this.targetCode = targetCode;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public RuleType getRuleType() {
    return ruleType;
  }

  public RuleTargetType getTargetType() {
    return targetType;
  }

  public String getTargetCode() {
    return targetCode;
  }

  public Jurisdiction getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(Jurisdiction jurisdiction) {
    this.jurisdiction = jurisdiction;
  }

  public boolean isActive() {
    return active;
  }
}
