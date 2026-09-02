package com.foreignerwarsaw.questionnaire.assessment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * The immutable, Phase-6-facing snapshot of one assessment (brief §37/§76/§77) - deliberately just
 * facts, never a legal conclusion (brief §90: no {@code PRIMARY_MATCH}/{@code
 * MORE_INFORMATION_REQUIRED} here). {@code answersByQuestionCode} contains only currently-{@code
 * applicable} answers (brief §28) keyed by the stable {@link
 * com.foreignerwarsaw.questionnaire.question.Question#getCode()} - an "unsure" answer is present in
 * neither the answer set nor missing entirely; it is simply absent from the map, same as a question
 * never reached, since Phase 6 must treat "answered unsure" identically to "not yet known" (brief
 * §37's own example never encodes "UNSURE" as a value).
 *
 * <p>No Phase 6 code exists yet to consume this (brief §76's contract only, no {@code
 * EligibilityRuleEvaluator}) - this record only defines the shape Phase 6 will read.
 */
public record AssessmentFacts(
    UUID assessmentId,
    UUID userId,
    UUID questionnaireVersionId,
    String questionnaireCode,
    int questionnaireVersionNumber,
    AssessmentStatus status,
    Instant completedAt,
    LocalDate evaluationDate,
    Map<String, Object> answersByQuestionCode) {}
