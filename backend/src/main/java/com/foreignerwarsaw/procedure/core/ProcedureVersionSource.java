package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Links a {@link ProcedureVersion} to at least one {@link OfficialSource} (brief §25) - a
 * dedicated, real-FK join entity rather than a polymorphic content-source table, matching the four
 * sibling associations (step/document/fee/threshold versions).
 */
@Entity
@Table(name = "procedure_version_sources")
public class ProcedureVersionSource {

  @EmbeddedId private ProcedureVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("procedureVersionId")
  @JoinColumn(name = "procedure_version_id")
  private ProcedureVersion procedureVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected ProcedureVersionSource() {}

  public ProcedureVersionSource(
      ProcedureVersion procedureVersion, OfficialSource officialSource, SourceRole role) {
    this.procedureVersion = procedureVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id = new ProcedureVersionSourceId(procedureVersion.getId(), officialSource.getId());
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
