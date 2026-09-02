package com.foreignerwarsaw.procedure.step;

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
@Table(name = "step_version_sources")
public class StepVersionSource {

  @EmbeddedId private StepVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("stepVersionId")
  @JoinColumn(name = "step_version_id")
  private StepVersion stepVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected StepVersionSource() {}

  public StepVersionSource(
      StepVersion stepVersion, OfficialSource officialSource, SourceRole role) {
    this.stepVersion = stepVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id = new StepVersionSourceId(stepVersion.getId(), officialSource.getId());
  }

  public StepVersion getStepVersion() {
    return stepVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
