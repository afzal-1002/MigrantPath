package com.foreignerwarsaw.rules.core;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RuleThresholdReferenceId implements Serializable {

  private UUID ruleVersionId;

  @Column(name = "threshold_code")
  private String thresholdCode;

  protected RuleThresholdReferenceId() {}

  public RuleThresholdReferenceId(UUID ruleVersionId, String thresholdCode) {
    this.ruleVersionId = ruleVersionId;
    this.thresholdCode = thresholdCode;
  }

  public String getThresholdCode() {
    return thresholdCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RuleThresholdReferenceId other)) return false;
    return Objects.equals(ruleVersionId, other.ruleVersionId)
        && Objects.equals(thresholdCode, other.thresholdCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleVersionId, thresholdCode);
  }
}
