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
 * {@link #active} is literally how "Warsaw is the only enabled city in V1" is implemented
 * (ARCHITECTURE.md §9) - enabling Kraków later is flipping this flag plus seeding its
 * districts/offices, not a deployment.
 */
@Entity
@Table(name = "cities")
public class City {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_id", nullable = false)
  private Country country;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "region_id", nullable = false)
  private Region region;

  @Column(nullable = false)
  private boolean active = false;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected City() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public Region getRegion() {
    return region;
  }

  public boolean isActive() {
    return active;
  }
}
