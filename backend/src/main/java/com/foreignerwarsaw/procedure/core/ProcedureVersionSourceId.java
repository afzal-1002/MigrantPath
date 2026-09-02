package com.foreignerwarsaw.procedure.core;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link ProcedureVersionSource} - a pure join row (docs/database/DATABASE.md
 * §0's convention), same pattern as Phase 3's {@code OfficeServiceId}.
 */
@Embeddable
public class ProcedureVersionSourceId implements Serializable {

  private UUID procedureVersionId;
  private UUID officialSourceId;

  protected ProcedureVersionSourceId() {}

  public ProcedureVersionSourceId(UUID procedureVersionId, UUID officialSourceId) {
    this.procedureVersionId = procedureVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcedureVersionSourceId other)) return false;
    return Objects.equals(procedureVersionId, other.procedureVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(procedureVersionId, officialSourceId);
  }
}
