package com.foreignerwarsaw.questionnaire.option;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

  List<QuestionOption> findByQuestionnaireQuestion_IdOrderBySortOrder(UUID questionnaireQuestionId);
}
