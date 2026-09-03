package com.foreignerwarsaw.rules.core;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RuleVersionSourceId implements Serializable {

  private UUID ruleVersionId;
  private UUID officialSourceId;

  protected RuleVersionSourceId() {}

  public RuleVersionSourceId(UUID ruleVersionId, UUID officialSourceId) {
    this.ruleVersionId = ruleVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RuleVersionSourceId other)) return false;
    return Objects.equals(ruleVersionId, other.ruleVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleVersionId, officialSourceId);
  }
}
