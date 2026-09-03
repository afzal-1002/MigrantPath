package com.foreignerwarsaw.procedure.admin.dto;

/** Counts only (brief §72/§73/§133) - never which users. */
public record ProcedureVersionImpactResponse(long activeUserCases) {}
