package com.foreignerwarsaw.questionnaire.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

  @Query(
      "SELECT a FROM Assessment a JOIN FETCH a.questionnaireVersion qv JOIN FETCH qv.questionnaire JOIN FETCH a.questionnaire WHERE a.id = :id")
  Optional<Assessment> findByIdFetchingVersion(@Param("id") UUID id);

  /** brief §34's one-IN_PROGRESS-per-questionnaire-identity rule, read path. */
  Optional<Assessment> findByUser_IdAndQuestionnaire_IdAndStatus(
      UUID userId, UUID questionnaireId, AssessmentStatus status);

  @Query(
      "SELECT a FROM Assessment a JOIN FETCH a.questionnaire WHERE a.user.id = :userId ORDER BY a.startedAt DESC")
  List<Assessment> findByUser_IdOrderByStartedAtDesc(@Param("userId") UUID userId);

  /**
   * Phase 9 impact analysis (brief §72/§133) - a count only, never which users, of assessments
   * permanently bound to this exact {@code QuestionnaireVersion}.
   */
  long countByQuestionnaireVersion_Id(UUID questionnaireVersionId);
}
