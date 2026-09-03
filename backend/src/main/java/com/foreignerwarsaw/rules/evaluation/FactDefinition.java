package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.common.evaluation.ComparisonOperator;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import java.util.Set;

/**
 * One entry in the Fact Registry (brief §12) - what a {@code RuleVersion.conditionTree} leaf may
 * reference by {@code fact} code. Reuses Phase 5's {@link QuestionType} directly for {@link
 * #valueType} rather than a parallel enum: a direct fact's semantic type already <i>is</i> its
 * {@code Question.questionType} (docs/product/QUESTION_CODES.md), and a derived fact (brief §13) is
 * given whichever {@code QuestionType} its computed value shape corresponds to (e.g. {@code
 * AGE_YEARS} is {@code INTEGER}).
 */
public record FactDefinition(
    String code,
    QuestionType valueType,
    boolean derived,
    Set<ComparisonOperator> allowedOperators) {}
