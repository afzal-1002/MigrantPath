package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.questionnaire.core.Questionnaire;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
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
import java.util.UUID;

/**
 * One user's run through a {@link QuestionnaireVersion} (docs/database/DATABASE.md §4, brief §23).
 * Authenticated-only (brief §31/§32 - see PHASE_5_REPORT.md "Deviations" for why this differs from
 * DATABASE.md's earlier anonymous-session sketch): {@code user} is never null. {@code
 * questionnaireVersion} is bound permanently at {@link #start} - never re-resolved to a newer
 * version while {@code IN_PROGRESS} even if one publishes later (brief §4).
 */
@Entity
@Table(name = "assessments")
public class Assessment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_version_id", nullable = false)
  private QuestionnaireVersion questionnaireVersion;

  /**
   * Denormalized from {@code questionnaireVersion.questionnaire} purely to back the "one
   * IN_PROGRESS assessment per user per questionnaire identity" DB constraint (brief §34) - never
   * read independently of {@link #questionnaireVersion}.
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "questionnaire_id", nullable = false)
  private Questionnaire questionnaire;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AssessmentStatus status = AssessmentStatus.IN_PROGRESS;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_updated_at", nullable = false)
  private Instant lastUpdatedAt;

  @jakarta.persistence.Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  protected Assessment() {}

  public static Assessment start(User user, QuestionnaireVersion questionnaireVersion, Instant at) {
    Assessment assessment = new Assessment();
    assessment.user = user;
    assessment.questionnaireVersion = questionnaireVersion;
    assessment.questionnaire = questionnaireVersion.getQuestionnaire();
    assessment.startedAt = at;
    assessment.lastUpdatedAt = at;
    return assessment;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public QuestionnaireVersion getQuestionnaireVersion() {
    return questionnaireVersion;
  }

  public Questionnaire getQuestionnaire() {
    return questionnaire;
  }

  public AssessmentStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public boolean isOwnedBy(UUID userId) {
    return user.getId().equals(userId);
  }

  public void touch(Instant at) {
    requireInProgress();
    this.lastUpdatedAt = at;
  }

  public void complete(Instant at) {
    requireInProgress();
    this.status = AssessmentStatus.COMPLETED;
    this.completedAt = at;
    this.lastUpdatedAt = at;
  }

  public void supersede(Instant at) {
    if (status != AssessmentStatus.IN_PROGRESS && status != AssessmentStatus.COMPLETED) {
      throw new IllegalStateException("Cannot supersede an assessment that is " + status);
    }
    this.status = AssessmentStatus.SUPERSEDED;
    this.lastUpdatedAt = at;
  }

  private void requireInProgress() {
    if (status != AssessmentStatus.IN_PROGRESS) {
      throw new IllegalStateException("Assessment is " + status + ", not IN_PROGRESS");
    }
  }
}
