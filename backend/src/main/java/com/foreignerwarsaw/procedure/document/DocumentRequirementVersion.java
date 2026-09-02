package com.foreignerwarsaw.procedure.document;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A full content snapshot for one {@link DocumentRequirement} within one {@link ProcedureVersion}
 * (docs/database/DATABASE.md §3, brief §15-17). {@link #validityPeriodDescription} is free text,
 * not a structured duration - "not older than 3 months" carries meaning a DURATION column would
 * flatten without gaining anything (brief §17: "do not convert complex legal statements into
 * oversimplified booleans if that loses meaning").
 */
@Entity
@Table(name = "document_requirement_versions")
public class DocumentRequirementVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_requirement_id", nullable = false)
  private DocumentRequirement documentRequirement;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_version_id", nullable = false)
  private ProcedureVersion procedureVersion;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_type", nullable = false, length = 20)
  private RequirementType requirementType;

  @Column(name = "required_by_default", nullable = false)
  private boolean requiredByDefault = true;

  @Column(name = "number_of_copies")
  private Integer numberOfCopies;

  @Column(name = "original_required")
  private Boolean originalRequired;

  @Column(name = "copy_required")
  private Boolean copyRequired;

  @Column(name = "translation_required")
  private Boolean translationRequired;

  @Column(name = "sworn_translation_required")
  private Boolean swornTranslationRequired;

  @Column(name = "apostille_required")
  private Boolean apostilleRequired;

  @Column(name = "legalisation_required")
  private Boolean legalisationRequired;

  @Column(name = "validity_period_description", length = 300)
  private String validityPeriodDescription;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected DocumentRequirementVersion() {}

  public DocumentRequirementVersion(
      DocumentRequirement documentRequirement,
      ProcedureVersion procedureVersion,
      String name,
      String description,
      RequirementType requirementType,
      boolean requiredByDefault,
      int sortOrder) {
    this.documentRequirement = documentRequirement;
    this.procedureVersion = procedureVersion;
    this.name = name;
    this.description = description;
    this.requirementType = requirementType;
    this.requiredByDefault = requiredByDefault;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public DocumentRequirement getDocumentRequirement() {
    return documentRequirement;
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public RequirementType getRequirementType() {
    return requirementType;
  }

  public boolean isRequiredByDefault() {
    return requiredByDefault;
  }

  public Integer getNumberOfCopies() {
    return numberOfCopies;
  }

  public Boolean getOriginalRequired() {
    return originalRequired;
  }

  public Boolean getTranslationRequired() {
    return translationRequired;
  }

  public Boolean getSwornTranslationRequired() {
    return swornTranslationRequired;
  }

  public Boolean getApostilleRequired() {
    return apostilleRequired;
  }

  public Boolean getLegalisationRequired() {
    return legalisationRequired;
  }

  public String getValidityPeriodDescription() {
    return validityPeriodDescription;
  }

  public String getNotes() {
    return notes;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
