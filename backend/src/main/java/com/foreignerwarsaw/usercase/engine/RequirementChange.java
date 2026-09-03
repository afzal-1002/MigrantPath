package com.foreignerwarsaw.usercase.engine;

/**
 * One structural difference between a case's current snapshot revision and the {@code
 * ProcedureVersion} currently active for its procedure (brief §27/§28). {@code changeType} is
 * always one of {@code ADDED}/{@code CHANGED}/{@code REMOVED} - {@code UNCHANGED} items are never
 * returned (brief §27 lists it as a possible per-item state, but an unchanged item is not a
 * "change" worth reporting to the user). {@code category} is {@code STEP}/{@code DOCUMENT}/ {@code
 * FEE}. {@code detail} is a short, stable, non-sensitive description of what changed (never raw
 * legal prose diffing - brief §35's "a field-level deterministic comparison is enough").
 */
public record RequirementChange(
    String changeType, String category, String stableCode, String title, String detail) {}
