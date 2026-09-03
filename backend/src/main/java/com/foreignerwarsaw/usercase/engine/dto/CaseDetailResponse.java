package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.procedure.core.dto.ProcedureAuthorityRefResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureOfficeRefResponse;
import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** brief §58 - fully structured, no raw JPA entity ever serialized. */
public record CaseDetailResponse(
    UUID id,
    String procedureCode,
    String procedureTitle,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant submittedAt,
    Instant completedAt,
    LocalDate evaluationDate,
    int revisionNumber,
    CaseProgressResponse progress,
    List<CaseStepResponse> steps,
    List<CaseDocumentResponse> documents,
    List<CaseFeeResponse> fees,
    List<ProcedureAuthorityRefResponse> authorities,
    List<ProcedureOfficeRefResponse> offices,
    List<SourceResponse> sources,
    boolean hasRequirementUpdates) {}
