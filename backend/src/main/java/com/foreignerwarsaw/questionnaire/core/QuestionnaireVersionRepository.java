package com.foreignerwarsaw.questionnaire.core;

import com.foreignerwarsaw.procedure.PublicationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionnaireVersionRepository extends JpaRepository<QuestionnaireVersion, UUID> {

  @Query("SELECT v FROM QuestionnaireVersion v JOIN FETCH v.questionnaire WHERE v.id = :id")
  Optional<QuestionnaireVersion> findByIdFetchingQuestionnaire(@Param("id") UUID id);

  /**
   * Phase 9 addition - additionally fetch-joins every actor field {@code
   * AdminQuestionnaireVersionDetailResponse} reads, so the (non-{@code @Transactional}) admin
   * controller can map the response after the service call returns without a
   * LazyInitializationException (see {@code ProcedureVersionRepository
   * #findByProcedure_IdAndVersionNumberFetchingActors}'s Javadoc for the same fix elsewhere).
   */
  @Query(
      """
      SELECT v FROM QuestionnaireVersion v
      JOIN FETCH v.questionnaire
      LEFT JOIN FETCH v.createdBy
      LEFT JOIN FETCH v.submittedBy
      LEFT JOIN FETCH v.approvedBy
      LEFT JOIN FETCH v.publishedBy
      WHERE v.id = :id
      """)
  Optional<QuestionnaireVersion> findByIdFetchingAll(@Param("id") UUID id);

  @Query(
      "SELECT v FROM QuestionnaireVersion v JOIN FETCH v.questionnaire WHERE v.questionnaire.id = :questionnaireId AND v.versionNumber = :versionNumber")
  Optional<QuestionnaireVersion> findByQuestionnaire_IdAndVersionNumber(
      @Param("questionnaireId") UUID questionnaireId, @Param("versionNumber") int versionNumber);

  /** Phase 9 addition - the admin-detail fetch-all variant of the lookup above. */
  @Query(
      """
      SELECT v FROM QuestionnaireVersion v
      JOIN FETCH v.questionnaire
      LEFT JOIN FETCH v.createdBy
      LEFT JOIN FETCH v.submittedBy
      LEFT JOIN FETCH v.approvedBy
      LEFT JOIN FETCH v.publishedBy
      WHERE v.questionnaire.id = :questionnaireId AND v.versionNumber = :versionNumber
      """)
  Optional<QuestionnaireVersion> findByQuestionnaire_IdAndVersionNumberFetchingActors(
      @Param("questionnaireId") UUID questionnaireId, @Param("versionNumber") int versionNumber);

  /**
   * The one authoritative Active-Version Predicate implementation for questionnaire content
   * (docs/database/DATABASE.md §0), mirroring {@code
   * ProcedureVersionRepository#findActivePublishedVersion} exactly - {@code EXCLUSIVE effectiveTo}.
   */
  @Query(
      """
      SELECT v FROM QuestionnaireVersion v JOIN FETCH v.questionnaire
      WHERE v.questionnaire.code = :questionnaireCode
        AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
        AND v.effectiveFrom <= :evaluationDate
        AND (v.effectiveTo IS NULL OR v.effectiveTo > :evaluationDate)
      """)
  Optional<QuestionnaireVersion> findActivePublishedVersion(
      @Param("questionnaireCode") String questionnaireCode,
      @Param("evaluationDate") LocalDate evaluationDate);

  @Query(
      "SELECT v FROM QuestionnaireVersion v WHERE v.questionnaire.id = :questionnaireId AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED")
  List<QuestionnaireVersion> findPublishedVersions(@Param("questionnaireId") UUID questionnaireId);

  @Query(
      "SELECT COALESCE(MAX(v.versionNumber), 0) FROM QuestionnaireVersion v WHERE v.questionnaire.id = :questionnaireId")
  int findMaxVersionNumber(@Param("questionnaireId") UUID questionnaireId);

  List<QuestionnaireVersion> findByStatus(PublicationStatus status);

  /** Phase 9 admin listing (brief §49) - newest first. */
  @Query(
      "SELECT v FROM QuestionnaireVersion v JOIN FETCH v.questionnaire WHERE v.questionnaire.id = :questionnaireId ORDER BY v.versionNumber DESC")
  List<QuestionnaireVersion> findByQuestionnaire_IdOrderByVersionNumberDesc(
      @Param("questionnaireId") UUID questionnaireId);

  /** Phase 9 admin dashboard addition (brief §16). */
  long countByStatus(PublicationStatus status);
}
