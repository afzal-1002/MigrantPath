package com.foreignerwarsaw.reference.geography;

import com.foreignerwarsaw.reference.country.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * {@code Region}, not a table named {@code Voivodeship} (Phase 0 decision, reaffirmed by this
 * phase's brief §9) - {@link #regionType} carries "VOIVODESHIP" as data so a future country's
 * states/provinces/cantons are just another value, never a new table or Java type.
 */
@Entity
@Table(name = "regions")
public class Region {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Column(name = "region_type", nullable = false, length = 30)
  private String regionType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_id", nullable = false)
  private Country country;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected Region() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public String getRegionType() {
    return regionType;
  }

  public Country getCountry() {
    return country;
  }

  public boolean isActive() {
    return active;
  }
}
