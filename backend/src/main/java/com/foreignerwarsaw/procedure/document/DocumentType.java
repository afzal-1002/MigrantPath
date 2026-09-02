package com.foreignerwarsaw.procedure.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A reusable document *concept* ("a Passport"), distinct from a procedure-specific {@link
 * DocumentRequirementVersion} ("provide a valid passport plus copies of pages X for this
 * procedure") - brief §18. Pure reference identity, not legal content.
 */
@Entity
@Table(name = "document_types")
public class DocumentType {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Column(columnDefinition = "text")
  private String description;

  @Column(nullable = false)
  private boolean active = true;

  protected DocumentType() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public boolean isActive() {
    return active;
  }
}
