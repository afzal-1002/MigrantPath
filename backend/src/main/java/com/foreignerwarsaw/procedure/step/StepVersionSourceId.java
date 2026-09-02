package com.foreignerwarsaw.procedure.step;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class StepVersionSourceId implements Serializable {

  private UUID stepVersionId;
  private UUID officialSourceId;

  protected StepVersionSourceId() {}

  public StepVersionSourceId(UUID stepVersionId, UUID officialSourceId) {
    this.stepVersionId = stepVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StepVersionSourceId other)) return false;
    return Objects.equals(stepVersionId, other.stepVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stepVersionId, officialSourceId);
  }
}
