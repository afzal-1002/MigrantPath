package com.foreignerwarsaw.procedure.document;

import com.foreignerwarsaw.procedure.core.Procedure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Stable document-requirement identity, same identity+version split as {@code ProcedureStep}/
 * {@code StepVersion} and for the same snapshot-readiness reason (DATABASE.md §8).
 */
@Entity
@Table(name = "document_requirements")
public class DocumentRequirement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @Column(name = "stable_code", nullable = false, length = 50)
  private String stableCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_type_id")
  private DocumentType documentType;

  protected DocumentRequirement() {}

  public DocumentRequirement(Procedure procedure, String stableCode, DocumentType documentType) {
    this.procedure = procedure;
    this.stableCode = stableCode;
    this.documentType = documentType;
  }

  public UUID getId() {
    return id;
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public String getStableCode() {
    return stableCode;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }
}
