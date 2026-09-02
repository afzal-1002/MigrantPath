package com.foreignerwarsaw.reference.country;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** {@code THIRD_COUNTRY} is deliberately never a row here - see ADR-006. */
@Entity
@Table(name = "country_groups")
public class CountryGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "group_type", nullable = false, length = 20)
  private CountryGroupType groupType;

  @Column(nullable = false)
  private boolean active = true;

  protected CountryGroup() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public CountryGroupType getGroupType() {
    return groupType;
  }

  public boolean isActive() {
    return active;
  }
}
