package com.foreignerwarsaw.questionnaire.dependency;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionDependencyRepository extends JpaRepository<QuestionDependency, UUID> {

  List<QuestionDependency> findByQuestionnaireQuestion_Id(UUID questionnaireQuestionId);

  /**
   * Every dependency row belonging to any question in one QuestionnaireVersion - used by both the
   * visibility engine (loaded once per assessment fetch) and cycle validation at publish time.
   */
  @Query(
      "SELECT d FROM QuestionDependency d WHERE d.questionnaireQuestion.questionnaireVersion.id = :versionId")
  List<QuestionDependency> findByQuestionnaireVersion_Id(@Param("versionId") UUID versionId);
}
