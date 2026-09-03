package com.foreignerwarsaw.recommendation.core;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
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
import java.util.UUID;

/**
 * One candidate {@link Procedure}'s classification within one {@link RecommendationRun} (brief §9)
 * - immutable once inserted, exactly like its parent run. {@link #procedureVersion} is nullable
 * only for {@link RecommendationType#UNAVAILABLE_FOR_ANALYSIS} (brief §28/§48 - no active PUBLISHED
 * content to point at, or the engine itself errored).
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recommendation_run_id", nullable = false)
  private RecommendationRun recommendationRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "procedure_id", nullable = false)
  private Procedure procedure;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "procedure_version_id")
  private ProcedureVersion procedureVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommendation_type", nullable = false, length = 30)
  private RecommendationType recommendationType;

  @Column(nullable = false)
  private int rank;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Recommendation() {}

  public Recommendation(
      RecommendationRun recommendationRun,
      Procedure procedure,
      ProcedureVersion procedureVersion,
      RecommendationType recommendationType,
      int rank,
      Instant createdAt) {
    this.recommendationRun = recommendationRun;
    this.procedure = procedure;
    this.procedureVersion = procedureVersion;
    this.recommendationType = recommendationType;
    this.rank = rank;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public RecommendationRun getRecommendationRun() {
    return recommendationRun;
  }

  public Procedure getProcedure() {
    return procedure;
  }

  public ProcedureVersion getProcedureVersion() {
    return procedureVersion;
  }

  public RecommendationType getRecommendationType() {
    return recommendationType;
  }

  public int getRank() {
    return rank;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
