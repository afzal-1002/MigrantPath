package com.foreignerwarsaw.procedure.authority;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProcedureVersionOfficeId implements Serializable {

  private UUID procedureVersionId;
  private UUID officeId;

  protected ProcedureVersionOfficeId() {}

  public ProcedureVersionOfficeId(UUID procedureVersionId, UUID officeId) {
    this.procedureVersionId = procedureVersionId;
    this.officeId = officeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcedureVersionOfficeId other)) return false;
    return Objects.equals(procedureVersionId, other.procedureVersionId)
        && Objects.equals(officeId, other.officeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(procedureVersionId, officeId);
  }
}
