package com.foreignerwarsaw.procedure.source;

import com.foreignerwarsaw.reference.authority.Authority;
import com.foreignerwarsaw.reference.geography.Jurisdiction;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every published legal-content item must trace to at least one of these (brief §25,
 * docs/database/DATABASE.md §3/§7). {@link #contentHash} is change-detection metadata only (brief
 * §50) - a hash change means "content may have changed," never itself evidence of legal review; no
 * crawler populates it automatically yet.
 */
@Entity
@Table(name = "official_sources")
public class OfficialSource {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "authority_id")
  private Authority authority;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(name = "source_url", nullable = false, length = 500)
  private String sourceUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "jurisdiction_id")
  private Jurisdiction jurisdiction;

  @Column(length = 5)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private SourceType sourceType;

  @Column(name = "publication_date")
  private LocalDate publicationDate;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "last_verified_at")
  private Instant lastVerifiedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 20)
  private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

  @Column(name = "content_hash", length = 128)
  private String contentHash;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(nullable = false)
  private boolean active = true;

  protected OfficialSource() {}

  public static OfficialSource draft(String title, String sourceUrl, SourceType sourceType) {
    OfficialSource source = new OfficialSource();
    source.title = title;
    source.sourceUrl = sourceUrl;
    source.sourceType = sourceType;
    return source;
  }

  public UUID getId() {
    return id;
  }

  public Authority getAuthority() {
    return authority;
  }

  public void setAuthority(Authority authority) {
    this.authority = authority;
  }

  public String getTitle() {
    return title;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public Jurisdiction getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(Jurisdiction jurisdiction) {
    this.jurisdiction = jurisdiction;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public LocalDate getPublicationDate() {
    return publicationDate;
  }

  public void setPublicationDate(LocalDate publicationDate) {
    this.publicationDate = publicationDate;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public void setEffectiveFrom(LocalDate effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public void setEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public VerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public Instant getLastVerifiedAt() {
    return lastVerifiedAt;
  }

  public Instant getLastCheckedAt() {
    return lastCheckedAt;
  }

  public boolean isActive() {
    return active;
  }

  /**
   * Only mutation path for {@link #verificationStatus}/{@link #lastVerifiedAt}/{@link
   * #lastCheckedAt} - always driven by a recorded {@link SourceVerification}, never set directly,
   * so the two never drift apart (brief §24).
   */
  public void applyVerification(SourceVerification verification) {
    this.verificationStatus = verification.getStatus();
    this.lastCheckedAt = verification.getCheckedAt();
    if (verification.getStatus() == VerificationStatus.VERIFIED) {
      this.lastVerifiedAt = verification.getCheckedAt();
    }
  }
}
