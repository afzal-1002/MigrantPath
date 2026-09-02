package com.foreignerwarsaw.reference.authority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Routing/reference information only (brief §14) - never confused with a future {@code Procedure}
 * (Phase 4+). "This office handles PESEL" is a fact about the office, not a legal rule.
 */
@Entity
@Table(name = "service_types")
public class ServiceType {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(nullable = false)
  private boolean active = true;

  protected ServiceType() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }
}
