package com.foreignerwarsaw.procedure.authority;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProcedureAuthorityId implements Serializable {

  private UUID procedureId;
  private UUID authorityId;

  @Enumerated(EnumType.STRING)
  private ProcedureAuthorityRole role;

  protected ProcedureAuthorityId() {}

  public ProcedureAuthorityId(UUID procedureId, UUID authorityId, ProcedureAuthorityRole role) {
    this.procedureId = procedureId;
    this.authorityId = authorityId;
    this.role = role;
  }

  public ProcedureAuthorityRole getRole() {
    return role;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcedureAuthorityId other)) return false;
    return Objects.equals(procedureId, other.procedureId)
        && Objects.equals(authorityId, other.authorityId)
        && role == other.role;
  }

  @Override
  public int hashCode() {
    return Objects.hash(procedureId, authorityId, role);
  }
}
