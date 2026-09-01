package com.foreignerwarsaw.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Only {@code USER} is seeded/used in Phase 2 (V6__seed_roles.sql). {@code ADMIN}, {@code
 * CONTENT_EDITOR}, {@code LEGAL_REVIEWER}, {@code CONSULTANT}, {@code COMPANY_ADMIN} are documented
 * future codes (docs/database/DATABASE.md §1) - adding one later is a data seed, not a schema or
 * code change.
 */
@Entity
@Table(name = "roles")
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  protected Role() {}

  public Role(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
