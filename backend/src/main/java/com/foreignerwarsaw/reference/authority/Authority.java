package com.foreignerwarsaw.reference.authority;

import com.foreignerwarsaw.reference.geography.Jurisdiction;
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
 * An institution with a legal mandate (UDSC, Mazowieckie Voivodeship Office, City of Warsaw) - as
 * opposed to {@link Office}, which is a physical place that institution operates
 * (docs/database/DATABASE.md §2). {@link #authorityType} is free-form, not an enum (brief §21) -
 * the brief gave no fixed vocabulary, and inventing one prematurely risks being wrong for a future
 * country's institutional structure.
 */
@Entity
@Table(name = "authorities")
public class Authority {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 300)
  private String canonicalName;

  @Column(name = "authority_type", nullable = false, length = 50)
  private String authorityType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "jurisdiction_id", nullable = false)
  private Jurisdiction jurisdiction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_authority_id")
  private Authority parentAuthority;

  @Column(name = "official_website", length = 300)
  private String officialWebsite;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  protected Authority() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public String getAuthorityType() {
    return authorityType;
  }

  public Jurisdiction getJurisdiction() {
    return jurisdiction;
  }

  /**
   * Null for every Phase 3 seed row (no hierarchy example verified yet) - the association exists
   * now so a future authority (e.g. a district-level UDSC branch under UDSC itself) doesn't need a
   * schema change to attach to a parent.
   */
  public Authority getParentAuthority() {
    return parentAuthority;
  }

  public String getOfficialWebsite() {
    return officialWebsite;
  }

  public boolean isActive() {
    return active;
  }
}
