package com.foreignerwarsaw.procedure.fee;

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

@Entity
@Table(name = "fee_version_sources")
public class FeeVersionSource {

  @EmbeddedId private FeeVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("feeVersionId")
  @JoinColumn(name = "fee_version_id")
  private FeeVersion feeVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected FeeVersionSource() {}

  public FeeVersionSource(FeeVersion feeVersion, OfficialSource officialSource, SourceRole role) {
    this.feeVersion = feeVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id = new FeeVersionSourceId(feeVersion.getId(), officialSource.getId());
  }

  public FeeVersion getFeeVersion() {
    return feeVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
