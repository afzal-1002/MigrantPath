package com.foreignerwarsaw.recommendation.core;

import com.foreignerwarsaw.questionnaire.assessment.Assessment;
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

/**
 * One immutable analysis of a completed {@link Assessment} (brief §36/§37) - unlike everything
 * Phase 4-6 versions, this is never edited in place; a re-analysis always creates a new row. {@link
 * #status} starts {@code RUNNING} and is set exactly once more, to {@code COMPLETED}/{@code
 * PARTIAL}/{@code FAILED}, by {@code RecommendationService} - never mutated again after that (brief
 * §37/§70).
 */
@Entity
@Table(name = "recommendation_runs")
public class RecommendationRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private Assessment assessment;

  @Column(name = "evaluation_date", nullable = false)
  private LocalDate evaluationDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RecommendationRunStatus status = RecommendationRunStatus.RUNNING;

  @Column(name = "recommendation_engine_version", nullable = false, length = 20)
  private String recommendationEngineVersion;

  @Column(name = "rule_engine_version", nullable = false, length = 20)
  private String ruleEngineVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected RecommendationRun() {}

  public static RecommendationRun start(
      User user,
      Assessment assessment,
      LocalDate evaluationDate,
      String recommendationEngineVersion,
      String ruleEngineVersion,
      Instant now) {
    RecommendationRun run = new RecommendationRun();
    run.user = user;
    run.assessment = assessment;
    run.evaluationDate = evaluationDate;
    run.recommendationEngineVersion = recommendationEngineVersion;
    run.ruleEngineVersion = ruleEngineVersion;
    run.createdAt = now;
    return run;
  }

  public void complete(RecommendationRunStatus status, Instant at) {
    this.status = status;
    this.completedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Assessment getAssessment() {
    return assessment;
  }

  public LocalDate getEvaluationDate() {
    return evaluationDate;
  }

  public RecommendationRunStatus getStatus() {
    return status;
  }

  public String getRecommendationEngineVersion() {
    return recommendationEngineVersion;
  }

  public String getRuleEngineVersion() {
    return ruleEngineVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
