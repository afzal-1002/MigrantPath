package com.foreignerwarsaw.rules.core;

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
 * {@code RuleVersion} -&gt; {@code OfficialSource} provenance (brief §22), the same shape as {@code
 * ThresholdVersionSource}. {@link SourceRole#LEGAL_BASIS} is meaningful here specifically - the
 * underlying statute/regulation a condition tree implements.
 */
@Entity
@Table(name = "rule_version_sources")
public class RuleVersionSource {

  @EmbeddedId private RuleVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("ruleVersionId")
  @JoinColumn(name = "rule_version_id")
  private RuleVersion ruleVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected RuleVersionSource() {}

  public RuleVersionSource(
      RuleVersion ruleVersion, OfficialSource officialSource, SourceRole role) {
    this.ruleVersion = ruleVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id = new RuleVersionSourceId(ruleVersion.getId(), officialSource.getId());
  }

  public RuleVersion getRuleVersion() {
    return ruleVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
