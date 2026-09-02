package com.foreignerwarsaw.procedure.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A procedure's primary category (docs/database/DATABASE.md §3, brief §5) - deliberately flat, no
 * self-referencing parent, since a hierarchy isn't needed by anything yet and can be added later
 * without changing this table's shape.
 */
@Entity
@Table(name = "procedure_categories")
public class ProcedureCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(nullable = false)
  private boolean active = true;

  protected ProcedureCategory() {}

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
