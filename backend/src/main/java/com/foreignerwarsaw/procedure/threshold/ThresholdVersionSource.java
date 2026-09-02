package com.foreignerwarsaw.procedure.threshold;

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
@Table(name = "threshold_version_sources")
public class ThresholdVersionSource {

  @EmbeddedId private ThresholdVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("thresholdVersionId")
  @JoinColumn(name = "threshold_version_id")
  private ThresholdVersion thresholdVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected ThresholdVersionSource() {}

  public ThresholdVersionSource(
      ThresholdVersion thresholdVersion, OfficialSource officialSource, SourceRole role) {
    this.thresholdVersion = thresholdVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id = new ThresholdVersionSourceId(thresholdVersion.getId(), officialSource.getId());
  }

  public ThresholdVersion getThresholdVersion() {
    return thresholdVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
