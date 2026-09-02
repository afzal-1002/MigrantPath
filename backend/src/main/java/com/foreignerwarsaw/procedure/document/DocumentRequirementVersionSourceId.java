package com.foreignerwarsaw.procedure.document;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DocumentRequirementVersionSourceId implements Serializable {

  private UUID documentRequirementVersionId;
  private UUID officialSourceId;

  protected DocumentRequirementVersionSourceId() {}

  public DocumentRequirementVersionSourceId(
      UUID documentRequirementVersionId, UUID officialSourceId) {
    this.documentRequirementVersionId = documentRequirementVersionId;
    this.officialSourceId = officialSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DocumentRequirementVersionSourceId other)) return false;
    return Objects.equals(documentRequirementVersionId, other.documentRequirementVersionId)
        && Objects.equals(officialSourceId, other.officialSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(documentRequirementVersionId, officialSourceId);
  }
}
