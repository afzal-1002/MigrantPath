package com.foreignerwarsaw.procedure.admin.dto;

import java.util.List;
import java.util.UUID;

/**
 * Structural diff between two {@code ProcedureVersion}s of the same {@code Procedure}, matched by
 * stable code (brief §68) - never by display title, mirroring Phase 8's {@code
 * CaseRequirementChangeService} precedent.
 */
public record ProcedureVersionDiffResponse(
    UUID fromVersionId,
    int fromVersionNumber,
    UUID toVersionId,
    int toVersionNumber,
    List<String> overviewChanges,
    List<String> stepsAdded,
    List<String> stepsRemoved,
    List<String> stepsChanged,
    List<String> documentsAdded,
    List<String> documentsRemoved,
    List<String> documentsChanged,
    List<String> feesAdded,
    List<String> feesRemoved,
    List<String> feesChanged) {}
