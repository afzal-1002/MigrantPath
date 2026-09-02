package com.foreignerwarsaw.questionnaire.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Exactly one of the scalar fields (or {@code selectedOptionCodes} for MULTI_SELECT) is non-null,
 * matching the owning question's {@code answerType} - never a stringified catch-all (brief §25).
 */
public record AnswerResponse(
    String stringValue,
    Boolean booleanValue,
    Long integerValue,
    BigDecimal decimalValue,
    LocalDate dateValue,
    String referenceCode,
    List<String> selectedOptionCodes,
    boolean unsure) {}
