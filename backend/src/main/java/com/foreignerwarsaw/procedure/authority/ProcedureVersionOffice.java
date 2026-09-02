package com.foreignerwarsaw.procedure.authority;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.reference.authority.Office;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * "This office can participate in this procedure" (brief §31) - never "this specific user must go
 * to this office," which depends on district/address/circumstances a future routing phase resolves.
 * Tied to procedure_version_id, not the bare procedure - which offices participate can change
 * alongside a content update.
 */
@Entity
@Table(name = "procedure_version_offices")
public class ProcedureVersionOffice {

  @EmbeddedId private ProcedureVersionOfficeId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("procedureVersionId")
  @JoinColumn(name = "procedure_version_id")
  private ProcedureVersion procedureVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officeId")
  @JoinColumn(name = "office_id")
  private Office office;

  @Column(columnDefinition = "text")
  private String notes;

  protected ProcedureVersionOffice() {}

  public ProcedureVersionOffice(ProcedureVersion procedureVersion, Office office) {
    this.procedureVersion = procedureVersion;
    this.office = office;
    this.id = new ProcedureVersionOfficeId(procedureVersion.getId(), office.getId());
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public Office getOffice() {
    return office;
  }
}
