package com.foreignerwarsaw.usercase.engine;

import java.util.List;
import java.util.UUID;

/**
 * {@code newActiveProcedureVersionId} is {@code null} when the case's snapshot already matches the
 * currently active {@code ProcedureVersion} - the "nothing to review" state (brief §30).
 */
public record RequirementChangeReport(
    boolean newerVersionAvailable,
    UUID newActiveProcedureVersionId,
    List<RequirementChange> changes) {}
