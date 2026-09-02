package com.foreignerwarsaw.reference.authority;

import com.foreignerwarsaw.reference.geography.City;
import com.foreignerwarsaw.reference.geography.District;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A physical place an {@link Authority} operates (docs/database/DATABASE.md §2) - a plain mutable
 * row with {@code validFrom}/{@code validTo}, not the full identity+version pattern legal content
 * uses (ADR-004 doesn't apply here: an office's address is an operational fact admins correct, not
 * a legal position).
 */
@Entity
@Table(name = "offices")
public class Office {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "authority_id", nullable = false)
  private Authority authority;

  @Column(name = "canonical_name", nullable = false, length = 300)
  private String canonicalName;

  @Column(length = 200)
  private String street;

  @Column(name = "building_number", length = 20)
  private String buildingNumber;

  @Column(name = "postal_code", length = 10)
  private String postalCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "district_id")
  private District district;

  @Column(length = 50)
  private String phone;

  @Column(length = 200)
  private String email;

  @Column(length = 300)
  private String website;

  @Column(name = "appointment_required")
  private Boolean appointmentRequired;

  @Column(name = "booking_url", length = 300)
  private String bookingUrl;

  /**
   * Deliberately unused in Phase 3 (brief §49) - genuinely irregular per-office schedules justify
   * JSONB over fixed weekday columns, but populating it needs its own verified source, not guessed
   * at alongside the address.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "opening_hours", columnDefinition = "jsonb")
  private String openingHours;

  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  @Column(name = "last_verified_at")
  private Instant lastVerifiedAt;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected Office() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public Authority getAuthority() {
    return authority;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public String getStreet() {
    return street;
  }

  public String getBuildingNumber() {
    return buildingNumber;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public City getCity() {
    return city;
  }

  public District getDistrict() {
    return district;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public String getWebsite() {
    return website;
  }

  public Boolean getAppointmentRequired() {
    return appointmentRequired;
  }

  public String getBookingUrl() {
    return bookingUrl;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public Instant getLastVerifiedAt() {
    return lastVerifiedAt;
  }

  public boolean isActive() {
    return active;
  }
}
