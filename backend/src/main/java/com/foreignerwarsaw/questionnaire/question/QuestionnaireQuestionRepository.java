package com.foreignerwarsaw.questionnaire.question;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionnaireQuestionRepository
    extends JpaRepository<QuestionnaireQuestion, UUID> {

  @Query(
      "SELECT qq FROM QuestionnaireQuestion qq JOIN FETCH qq.question WHERE qq.questionnaireVersion.id = :versionId ORDER BY qq.sortOrder")
  List<QuestionnaireQuestion> findByQuestionnaireVersion_IdOrderBySortOrder(
      @Param("versionId") UUID versionId);
}
