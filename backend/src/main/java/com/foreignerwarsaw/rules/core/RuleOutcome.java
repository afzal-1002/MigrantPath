package com.foreignerwarsaw.rules.core;

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
 * Forward-looking extension point for rule composition/reuse (docs/database/DATABASE.md §5, brief
 * §36) - e.g. a future reusable sub-rule other rules could reference by outcome code.
 * <b>Deliberately not wired into {@code RuleEvaluator} in Phase 6</b> (brief §24: "avoid allowing
 * one rule to depend on another RuleVersion unless clearly needed" - every Phase 6 condition tree
 * is standalone). Schema placeholder only - do not "finish" this by adding evaluation logic without
 * a concrete composable-rule need driving it.
 */
@Entity
@Table(name = "rule_outcomes")
public class RuleOutcome {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rule_version_id", nullable = false)
  private RuleVersion ruleVersion;

  @Column(name = "outcome_code", nullable = false, length = 50)
  private String outcomeCode;

  @Column(length = 500)
  private String description;

  protected RuleOutcome() {}

  public RuleOutcome(RuleVersion ruleVersion, String outcomeCode, String description) {
    this.ruleVersion = ruleVersion;
    this.outcomeCode = outcomeCode;
    this.description = description;
  }

  public UUID getId() {
    return id;
  }

  public RuleVersion getRuleVersion() {
    return ruleVersion;
  }

  public String getOutcomeCode() {
    return outcomeCode;
  }

  public String getDescription() {
    return description;
  }
}
