package com.foreignerwarsaw.questionnaire.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, UUID> {

  @Query(
      "SELECT a FROM AssessmentAnswer a JOIN FETCH a.question WHERE a.assessment.id = :assessmentId")
  List<AssessmentAnswer> findByAssessment_Id(@Param("assessmentId") UUID assessmentId);

  Optional<AssessmentAnswer> findByAssessment_IdAndQuestion_Id(UUID assessmentId, UUID questionId);
}
