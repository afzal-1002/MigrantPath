package com.foreignerwarsaw.questionnaire.assessment.dto;

import com.foreignerwarsaw.questionnaire.assessment.MissingQuestion;
import com.foreignerwarsaw.questionnaire.core.dto.QuestionDefinitionResponse;
import com.foreignerwarsaw.questionnaire.core.dto.SectionResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backend-authoritative view of one assessment (brief §30) - {@code questions} contains ONLY
 * currently-visible questions with their current answer embedded (brief §55: a hidden answer never
 * appears here at all, not even as a "hidden" flag) and {@code sections} contains only sections
 * that have at least one currently-visible question, in the order they should render.
 */
public record AssessmentDetailResponse(
    UUID id,
    String status,
    String questionnaireCode,
    UUID questionnaireVersionId,
    Instant startedAt,
    Instant completedAt,
    int progressPercent,
    List<SectionResponse> sections,
    List<QuestionDefinitionResponse> questions,
    List<MissingQuestion> missingRequiredQuestions) {}
