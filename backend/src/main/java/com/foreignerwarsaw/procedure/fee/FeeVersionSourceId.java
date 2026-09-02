package com.foreignerwarsaw.procedure.fee;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FeeVersionSourceId implements Serializable {

  private UUID feeVersionId;
  private UUID officialSourceId;

  protected FeeVersionSourceId() {}

  public FeeVersionSourceId(UUID feeVersionId, UUID officialSourceId) {
    this.feeVersionId = feeVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FeeVersionSourceId other)) return false;
    return Objects.equals(feeVersionId, other.feeVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(feeVersionId, officialSourceId);
  }
}
