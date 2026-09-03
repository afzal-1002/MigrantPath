package com.foreignerwarsaw.rules.core;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * The queryable companion to {@link RuleVersion#getConditionTree()}'s opaque JSONB
 * (docs/database/DATABASE.md §5, brief §21) - "which rules depend on threshold X" as a plain
 * indexed query rather than parsing JSON at query time. Populated by {@code RuleVersionService}
 * whenever a {@link RuleVersion} is saved, by walking the condition tree once and extracting every
 * {@code threshold} reference - always rebuilt from the tree, never hand-maintained, so the two can
 * never drift (brief §21's "ensure JSON condition and reference table cannot drift").
 */
@Entity
@Table(name = "rule_threshold_references")
public class RuleThresholdReference {

  @EmbeddedId private RuleThresholdReferenceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("ruleVersionId")
  @JoinColumn(name = "rule_version_id")
  private RuleVersion ruleVersion;

  protected RuleThresholdReference() {}

  public RuleThresholdReference(RuleVersion ruleVersion, String thresholdCode) {
    this.ruleVersion = ruleVersion;
    this.id = new RuleThresholdReferenceId(ruleVersion.getId(), thresholdCode);
  }

  public RuleVersion getRuleVersion() {
    return ruleVersion;
  }

  public String getThresholdCode() {
    return id.getThresholdCode();
  }
}
