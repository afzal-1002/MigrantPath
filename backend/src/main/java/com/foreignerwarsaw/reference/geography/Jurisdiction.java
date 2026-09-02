package com.foreignerwarsaw.reference.geography;

import com.foreignerwarsaw.reference.country.Country;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * The legal/procedural scope a future {@code Procedure}/{@code Rule}/{@code Authority} operates at
 * - distinct from the {@link Region}/{@link City}/{@link District} geography entities, which record
 * *where a place is*, not *at what legal scope a rule applies* (ARCHITECTURE.md §9).
 *
 * <p>Modeled as a self-referencing tree ({@link #parentJurisdiction}) per this phase's brief §8,
 * refining Phase 0's original flat-FK sketch - see V14's migration comment for the full rationale.
 * {@link #region}/{@link #city} are still carried directly so "find the jurisdiction for Warsaw" is
 * a plain indexed lookup, not a recursive traversal.
 */
@Entity
@Table(name = "jurisdictions")
public class Jurisdiction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "jurisdiction_type", nullable = false, length = 20)
  private JurisdictionType jurisdictionType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_jurisdiction_id")
  private Jurisdiction parentJurisdiction;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_id", nullable = false)
  private Country country;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "region_id")
  private Region region;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "city_id")
  private City city;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected Jurisdiction() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public JurisdictionType getJurisdictionType() {
    return jurisdictionType;
  }

  public Jurisdiction getParentJurisdiction() {
    return parentJurisdiction;
  }

  public Region getRegion() {
    return region;
  }

  public City getCity() {
    return city;
  }

  public boolean isActive() {
    return active;
  }
}
