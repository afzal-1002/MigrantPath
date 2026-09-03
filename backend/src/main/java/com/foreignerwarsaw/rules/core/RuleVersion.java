package com.foreignerwarsaw.rules.core;

import com.foreignerwarsaw.procedure.PublicationStateMachine;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.user.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Versioned, source-backed rule content (docs/database/DATABASE.md §5, ADR-009). Reuses {@link
 * PublicationStatus}/{@link PublicationStateMachine} directly (same reuse {@code
 * QuestionnaireVersion} already made, brief §55: "if reusing Phase 4 lifecycle is clean... is
 * acceptable"). Once {@code PUBLISHED}, immutable - a content change means a new {@code DRAFT}
 * version via {@link RuleVersionService#createDraftFrom}, never an edit in place (brief §17).
 *
 * <p>{@link #conditionTree} is raw JSON text (the same {@code @JdbcTypeCode(SqlTypes.JSON)}
 * convention as {@code QuestionDependency.expectedValue} and {@code Office.openingHours}) -
 * validated by {@code com.foreignerwarsaw.rules.condition.ConditionTreeValidator} on write, never
 * by the database (brief §66/§67). See DATABASE.md §5 for why JSONB over normalized condition rows.
 */
@Entity
@Table(name = "rule_versions")
public class RuleVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rule_id", nullable = false)
  private Rule rule;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PublicationStatus status = PublicationStatus.DRAFT;

  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "condition_tree", nullable = false, columnDefinition = "jsonb")
  private String conditionTree;

  @Column(name = "condition_schema_version", nullable = false)
  private int conditionSchemaVersion = 1;

  @Column(name = "explanation_key", length = 200)
  private String explanationKey;

  @Column(name = "change_summary", length = 1000)
  private String changeSummary;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submitted_by")
  private User submittedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by")
  private User approvedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "published_by")
  private User publishedBy;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @jakarta.persistence.Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected RuleVersion() {}

  public static RuleVersion draft(
      Rule rule, int versionNumber, String conditionTree, String explanationKey, User createdBy) {
    RuleVersion version = new RuleVersion();
    version.rule = rule;
    version.versionNumber = versionNumber;
    version.conditionTree = conditionTree;
    version.explanationKey = explanationKey;
    version.createdBy = createdBy;
    return version;
  }

  public UUID getId() {
    return id;
  }

  public Rule getRule() {
    return rule;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public PublicationStatus getStatus() {
    return status;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public String getConditionTree() {
    return conditionTree;
  }

  public int getConditionSchemaVersion() {
    return conditionSchemaVersion;
  }

  public String getExplanationKey() {
    return explanationKey;
  }

  public User getPublishedBy() {
    return publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void updateDraftContent(String conditionTree, String explanationKey) {
    requireMutable();
    this.conditionTree = conditionTree;
    this.explanationKey = explanationKey;
  }

  public void submitForReview(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.IN_REVIEW);
    this.status = PublicationStatus.IN_REVIEW;
    this.submittedBy = actor;
    this.submittedAt = at;
  }

  public void sendBackToDraft() {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.DRAFT);
    this.status = PublicationStatus.DRAFT;
  }

  public void approve(User actor, Instant at) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.APPROVED);
    this.status = PublicationStatus.APPROVED;
    this.approvedBy = actor;
    this.approvedAt = at;
  }

  /**
   * Only the mechanical transition + timestamp/actor bookkeeping - {@link RulePublishingService}
   * owns publish-readiness validation and the "close the previous active version" side effect,
   * matching {@code ProcedureVersion#markPublished}'s split.
   */
  public void markPublished(User actor, Instant at, LocalDate effectiveFrom) {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.PUBLISHED);
    this.status = PublicationStatus.PUBLISHED;
    this.publishedBy = actor;
    this.publishedAt = at;
    this.effectiveFrom = effectiveFrom;
  }

  public void archive() {
    PublicationStateMachine.requireAllowed(status, PublicationStatus.ARCHIVED);
    this.status = PublicationStatus.ARCHIVED;
  }

  public void closeEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  private void requireMutable() {
    if (status == PublicationStatus.PUBLISHED || status == PublicationStatus.ARCHIVED) {
      throw new IllegalStateException(
          "RuleVersion content is immutable once "
              + status
              + " - create a new draft version instead");
    }
  }
}
