package com.foreignerwarsaw.reference.country;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Pure reference data (docs/database/DATABASE.md §2, ARCHITECTURE.md §7) - a nationality is never
 * conflated with a legal classification here. {@code code} (ISO 3166-1 alpha-2) is the stable
 * identifier used everywhere else (rule conditions, URLs, foreign keys) - never the display name,
 * which may be translated later (brief §39) without the identity ever changing. See V7/V8
 * migrations and docs/reference/REFERENCE_DATA_SOURCES.md for seed provenance.
 *
 * <p>Not every seeded row is an officially assigned ISO 3166-1 code (V18) - see {@link
 * #codeStandard}/{@link #officiallyAssigned}. Currently 249 of the 250 seeded rows are; the
 * exception is {@code XK} (Kosovo), a user-assigned code this application supports anyway because
 * it's operationally useful, without ever claiming it's part of the ISO standard.
 */
@Entity
@Table(name = "countries")
public class Country {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 2)
  private String code;

  @Column(name = "alpha3_code", length = 3)
  private String alpha3Code;

  @Column(name = "numeric_code", length = 3)
  private String numericCode;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "code_standard", nullable = false, length = 20)
  private CountryCodeStandard codeStandard = CountryCodeStandard.ISO_3166_1;

  @Column(name = "officially_assigned", nullable = false)
  private boolean officiallyAssigned = true;

  @Column(columnDefinition = "text")
  private String notes;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Country() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getAlpha3Code() {
    return alpha3Code;
  }

  public String getNumericCode() {
    return numericCode;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public boolean isActive() {
    return active;
  }

  public Integer getDisplayOrder() {
    return displayOrder;
  }

  public CountryCodeStandard getCodeStandard() {
    return codeStandard;
  }

  /** {@code false} only for {@code XK} (Kosovo) as of Phase 3 - see the class Javadoc. */
  public boolean isOfficiallyAssigned() {
    return officiallyAssigned;
  }

  public String getNotes() {
    return notes;
  }
}
