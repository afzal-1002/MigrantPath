package com.foreignerwarsaw.questionnaire.core;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, UUID> {

  Optional<Questionnaire> findByCode(String code);
}
