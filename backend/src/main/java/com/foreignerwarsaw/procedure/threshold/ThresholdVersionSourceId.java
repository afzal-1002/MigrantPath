package com.foreignerwarsaw.procedure.threshold;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ThresholdVersionSourceId implements Serializable {

  private UUID thresholdVersionId;
  private UUID officialSourceId;

  protected ThresholdVersionSourceId() {}

  public ThresholdVersionSourceId(UUID thresholdVersionId, UUID officialSourceId) {
    this.thresholdVersionId = thresholdVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ThresholdVersionSourceId other)) return false;
    return Objects.equals(thresholdVersionId, other.thresholdVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(thresholdVersionId, officialSourceId);
  }
}
