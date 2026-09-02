package com.foreignerwarsaw.procedure.document;

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
@Table(name = "document_requirement_version_sources")
public class DocumentRequirementVersionSource {

  @EmbeddedId private DocumentRequirementVersionSourceId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("documentRequirementVersionId")
  @JoinColumn(name = "document_requirement_version_id")
  private DocumentRequirementVersion documentRequirementVersion;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("officialSourceId")
  @JoinColumn(name = "official_source_id")
  private OfficialSource officialSource;

  @Enumerated(EnumType.STRING)
  private SourceRole role = SourceRole.PRIMARY;

  protected DocumentRequirementVersionSource() {}

  public DocumentRequirementVersionSource(
      DocumentRequirementVersion documentRequirementVersion,
      OfficialSource officialSource,
      SourceRole role) {
    this.documentRequirementVersion = documentRequirementVersion;
    this.officialSource = officialSource;
    this.role = role;
    this.id =
        new DocumentRequirementVersionSourceId(
            documentRequirementVersion.getId(), officialSource.getId());
  }

  public DocumentRequirementVersion getDocumentRequirementVersion() {
    return documentRequirementVersion;
  }

  public OfficialSource getOfficialSource() {
    return officialSource;
  }

  public SourceRole getRole() {
    return role;
  }
}
