package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.core.dto.ProcedureDetailResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only, unauthenticated (brief §36-38). Only currently active PUBLISHED content is
 * ever returned - DRAFT/IN_REVIEW/APPROVED versions, and PUBLISHED versions outside their effective
 * range, are invisible here regardless of how they're queried (see {@link ProcedureQueryService}).
 * Never returns an eligibility determination or recommendation (brief §90) - "what is this
 * procedure," never "does the current user qualify."
 */
@RestController
@RequestMapping("/api/v1/procedures")
@Tag(name = "Procedures")
public class ProcedureController {

  private final ProcedureQueryService procedureQueryService;

  public ProcedureController(ProcedureQueryService procedureQueryService) {
    this.procedureQueryService = procedureQueryService;
  }

  @Operation(summary = "Procedures with a currently active published version")
  @GetMapping
  public List<ProcedureSummaryResponse> list() {
    return procedureQueryService.listPublished().stream()
        .map(ProcedureSummaryResponse::from)
        .toList();
  }

  @Operation(summary = "A procedure's currently active published version, in full")
  @GetMapping("/{code}")
  public ProcedureDetailResponse get(@PathVariable String code) {
    return procedureQueryService.getPublishedDetail(code);
  }
}
