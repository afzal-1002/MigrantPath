package com.foreignerwarsaw.questionnaire.core.dto;

import java.util.List;
import java.util.UUID;

public record QuestionnaireStructureResponse(
    String questionnaireCode,
    UUID questionnaireVersionId,
    int versionNumber,
    String title,
    String description,
    List<SectionResponse> sections,
    List<QuestionDefinitionResponse> questions) {}
