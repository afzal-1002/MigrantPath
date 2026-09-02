package com.foreignerwarsaw.reference.country;

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
 * Time-bounded on purpose (V9, ADR-006) - the UK's EU membership ending 2020-01-31 is the case a
 * static boolean would get wrong for any pre-2020 evaluation.
 *
 * <p>{@code validTo} is <b>inclusive</b> (brief §16) - deliberately different from the
 * Procedure/Rule Active-Version Predicate's exclusive {@code effective_to}; see ADR-006's "Temporal
 * convention" section for why.
 *
 * <p>{@link #provenanceStatus} (V19) exists so a pre-2000 accession date that was compiled from
 * general historical knowledge rather than a single authoritative source is visibly {@code DRAFT},
 * not indistinguishable from a fully {@code VERIFIED} row - see ADR-006's provenance section. This
 * makes the distinction queryable rather than only living in a migration comment, so a future
 * legally-significant rule evaluation can choose to require {@code VERIFIED} provenance where
 * appropriate, without that requirement silently depending on unverified data.
 */
@Entity
@Table(name = "country_group_memberships")
public class CountryGroupMembership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_id", nullable = false)
  private Country country;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "country_group_id", nullable = false)
  private CountryGroup countryGroup;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "provenance_status", nullable = false, length = 20)
  private MembershipProvenanceStatus provenanceStatus = MembershipProvenanceStatus.VERIFIED;

  protected CountryGroupMembership() {}

  public Country getCountry() {
    return country;
  }

  public CountryGroup getCountryGroup() {
    return countryGroup;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
  }

  public MembershipProvenanceStatus getProvenanceStatus() {
    return provenanceStatus;
  }

  /** Inclusive on both ends - see class Javadoc. */
  public boolean coversDate(LocalDate date) {
    boolean startsInTime = !validFrom.isAfter(date);
    boolean notYetEnded = validTo == null || !validTo.isBefore(date);
    return startsInTime && notYetEnded;
  }
}
