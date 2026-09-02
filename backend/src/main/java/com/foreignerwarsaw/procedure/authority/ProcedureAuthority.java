package com.foreignerwarsaw.procedure.authority;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.reference.authority.Authority;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * "This procedure legally/operationally involves this authority in this role" (brief §30) - kept at
 * the {@link Procedure} identity level, not per-version: which authorities are involved changes
 * rarely, never as a side effect of ordinary content wording edits. Never hard-codes "if Warsaw
 * then Mazowieckie Office" in service logic - that association lives here, as data.
 */
@Entity
@Table(name = "procedure_authorities")
public class ProcedureAuthority {

  @EmbeddedId private ProcedureAuthorityId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("procedureId")
  @JoinColumn(name = "procedure_id")
  private Procedure procedure;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("authorityId")
  @JoinColumn(name = "authority_id")
  private Authority authority;

  @Column(columnDefinition = "text")
  private String notes;

  protected ProcedureAuthority() {}

  public ProcedureAuthority(Procedure procedure, Authority authority, ProcedureAuthorityRole role) {
    this.procedure = procedure;
    this.authority = authority;
    this.id = new ProcedureAuthorityId(procedure.getId(), authority.getId(), role);
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public Authority getAuthority() {
    return authority;
  }

  public ProcedureAuthorityRole getRole() {
    return id.getRole();
  }
}
