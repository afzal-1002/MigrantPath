package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.category.ProcedureCategory;
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
import java.util.UUID;

/**
 * Stable procedure identity (docs/database/DATABASE.md §3) - answers only "what is this procedure,"
 * never "does a given user qualify" (that's Phase 6's Rule/RuleVersion, referenced from nowhere in
 * this class). {@code code} is the stable business identifier every rule condition/URL/UserCase
 * eventually points at - never the display name.
 */
@Entity
@Table(name = "procedures")
public class Procedure {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private ProcedureCategory category;

  @Column(name = "canonical_name", nullable = false, length = 300)
  private String canonicalName;

  @Column(name = "short_description", length = 500)
  private String shortDescription;

  @Column(name = "procedure_type", length = 50)
  private String procedureType;

  @Enumerated(EnumType.STRING)
  @Column(name = "jurisdiction_scope", nullable = false, length = 20)
  private JurisdictionScope jurisdictionScope;

  @Column(nullable = false)
  private boolean active = true;

  /**
   * Phase 7's optional, reviewed-only ranking signal (docs/recommendations/RANKING_POLICY.md) -
   * {@code null} for every Procedure until a deliberate, source-reviewed content pass sets it;
   * {@code RecommendationRanker} only distinguishes {@code PRIMARY_MATCH} from {@code
   * POSSIBLE_ALTERNATIVE} by this column when at least one candidate in a run has it set.
   */
  @Column(name = "recommendation_priority")
  private Integer recommendationPriority;

  protected Procedure() {}

  public static Procedure create(
      String code,
      ProcedureCategory category,
      String canonicalName,
      String shortDescription,
      JurisdictionScope jurisdictionScope) {
    Procedure procedure = new Procedure();
    procedure.code = code;
    procedure.category = category;
    procedure.canonicalName = canonicalName;
    procedure.shortDescription = shortDescription;
    procedure.jurisdictionScope = jurisdictionScope;
    return procedure;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public ProcedureCategory getCategory() {
    return category;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public JurisdictionScope getJurisdictionScope() {
    return jurisdictionScope;
  }

  public boolean isActive() {
    return active;
  }

  public Integer getRecommendationPriority() {
    return recommendationPriority;
  }
}
