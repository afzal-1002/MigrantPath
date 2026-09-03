package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.admin.dto.AdminReviewResponse;
import com.foreignerwarsaw.admin.dto.ReviewDecisionRequest;
import com.foreignerwarsaw.admin.dto.ValidationResponse;
import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.procedure.admin.dto.AddFeeRequest;
import com.foreignerwarsaw.procedure.admin.dto.AdminProcedureSummaryResponse;
import com.foreignerwarsaw.procedure.admin.dto.AdminProcedureVersionDetailResponse;
import com.foreignerwarsaw.procedure.admin.dto.ProcedureVersionAdminResponse;
import com.foreignerwarsaw.procedure.admin.dto.ProcedureVersionDiffResponse;
import com.foreignerwarsaw.procedure.admin.dto.ProcedureVersionImpactResponse;
import com.foreignerwarsaw.procedure.admin.dto.PublishRequest;
import com.foreignerwarsaw.procedure.admin.dto.UpdateDocumentRequirementRequest;
import com.foreignerwarsaw.procedure.admin.dto.UpdateFeeRequest;
import com.foreignerwarsaw.procedure.admin.dto.UpdateProcedureVersionRequest;
import com.foreignerwarsaw.procedure.admin.dto.UpdateStepRequest;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedurePublishingService;
import com.foreignerwarsaw.procedure.core.ProcedureRepository;
import com.foreignerwarsaw.procedure.core.ProcedureService;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.procedure.core.ProcedureVersionService;
import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.document.DocumentRequirementService;
import com.foreignerwarsaw.procedure.fee.FeeService;
import com.foreignerwarsaw.procedure.step.ProcedureStepService;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevisionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's Procedure admin surface (brief §17-§25/§56). Read/list endpoints here are new; the
 * mutating create/add-step/add-document/add-fee/create-source/attach-source endpoints that already
 * existed from Phase 4 stay at their original {@code /api/v1/internal/content/**} path unchanged
 * (brief §80's "reuse existing endpoints rather than duplicate") - this controller only exposes
 * what Phase 4 didn't: listing, version detail, edit/remove/reorder of draft content,
 * review-workflow actions routed through {@link ContentReviewCoordinator}, validation, diff, and
 * impact analysis. Authorization is enforced by {@code SecurityConfig}'s URL matchers, the same
 * style as the Phase 4 controller.
 */
@RestController
@RequestMapping("/api/v1/admin/procedures")
@Tag(name = "Admin - Procedures")
public class AdminProcedureController {

  private final ProcedureService procedureService;
  private final ProcedureRepository procedureRepository;
  private final ProcedureVersionService procedureVersionService;
  private final ProcedureVersionRepository procedureVersionRepository;
  private final ProcedurePublishingService procedurePublishingService;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final ProcedureStepService procedureStepService;
  private final DocumentRequirementService documentRequirementService;
  private final FeeService feeService;
  private final ProcedureAdminService procedureAdminService;
  private final ProcedureVersionDiffService diffService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final UserCaseSnapshotRevisionRepository userCaseSnapshotRevisionRepository;
  private final UserAccountService userAccountService;

  public AdminProcedureController(
      ProcedureService procedureService,
      ProcedureRepository procedureRepository,
      ProcedureVersionService procedureVersionService,
      ProcedureVersionRepository procedureVersionRepository,
      ProcedurePublishingService procedurePublishingService,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      ProcedureStepService procedureStepService,
      DocumentRequirementService documentRequirementService,
      FeeService feeService,
      ProcedureAdminService procedureAdminService,
      ProcedureVersionDiffService diffService,
      ContentReviewCoordinator reviewCoordinator,
      UserCaseSnapshotRevisionRepository userCaseSnapshotRevisionRepository,
      UserAccountService userAccountService) {
    this.procedureService = procedureService;
    this.procedureRepository = procedureRepository;
    this.procedureVersionService = procedureVersionService;
    this.procedureVersionRepository = procedureVersionRepository;
    this.procedurePublishingService = procedurePublishingService;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.procedureStepService = procedureStepService;
    this.documentRequirementService = documentRequirementService;
    this.feeService = feeService;
    this.procedureAdminService = procedureAdminService;
    this.diffService = diffService;
    this.reviewCoordinator = reviewCoordinator;
    this.userCaseSnapshotRevisionRepository = userCaseSnapshotRevisionRepository;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "List procedures with their active/latest version summary")
  @GetMapping
  public List<AdminProcedureSummaryResponse> list() {
    LocalDate today = LocalDate.now();
    return procedureRepository.findAllFetchingCategory().stream()
        .map(
            p -> {
              ProcedureVersion active =
                  procedureVersionRepository
                      .findActivePublishedVersion(p.getId(), today)
                      .orElse(null);
              List<ProcedureVersion> all =
                  procedureVersionRepository.findByProcedure_IdOrderByVersionNumberDesc(p.getId());
              ProcedureVersion latest = all.isEmpty() ? null : all.get(0);
              return AdminProcedureSummaryResponse.from(p, active, latest);
            })
        .toList();
  }

  @Operation(summary = "Procedure identity + every version (brief §18)")
  @GetMapping("/{code}")
  public List<ProcedureVersionAdminResponse> versionHistory(@PathVariable String code) {
    Procedure procedure = procedureService.getByCode(code);
    return procedureVersionRepository
        .findByProcedure_IdOrderByVersionNumberDesc(procedure.getId())
        .stream()
        .map(ProcedureVersionAdminResponse::from)
        .toList();
  }

  @Operation(summary = "One version's full editor payload (brief §19)")
  @GetMapping("/{code}/versions/{versionNumber}")
  public AdminProcedureVersionDetailResponse versionDetail(
      @PathVariable String code, @PathVariable int versionNumber) {
    ProcedureVersion version = version(code, versionNumber);
    return AdminProcedureVersionDetailResponse.from(
        version,
        procedureStepService.listForVersion(version.getId()),
        documentRequirementService.listForVersion(version.getId()),
        feeService.listForVersion(version.getId()),
        procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()));
  }

  @Operation(summary = "Create a new draft copied from this version (brief §10)")
  @PostMapping("/{code}/versions/{versionNumber}/copy")
  public ResponseEntity<AdminProcedureVersionDetailResponse> copyVersion(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion source = version(code, versionNumber);
    ProcedureVersion copy = procedureAdminService.createDraftFrom(source.getId(), actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(copy));
  }

  @Operation(summary = "Edit a DRAFT version's overview fields (brief §20)")
  @PatchMapping("/{code}/versions/{versionNumber}")
  public AdminProcedureVersionDetailResponse updateOverview(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody UpdateProcedureVersionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.updateDraft(
        version.getId(),
        request.title(),
        request.summary(),
        request.description(),
        request.effectiveFrom(),
        request.changeSummary(),
        actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Edit a step already on a DRAFT version (brief §21)")
  @PatchMapping("/{code}/versions/{versionNumber}/steps/{stepId}")
  public AdminProcedureVersionDetailResponse updateStep(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @PathVariable UUID stepId,
      @Valid @RequestBody UpdateStepRequest request) {
    procedureStepService.updateStep(
        stepId,
        request.title(),
        request.description(),
        request.stepType(),
        request.sortOrder(),
        request.mandatory());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Remove a step from a DRAFT version (brief §21)")
  @DeleteMapping("/{code}/versions/{versionNumber}/steps/{stepId}")
  public AdminProcedureVersionDetailResponse removeStep(
      @PathVariable String code, @PathVariable int versionNumber, @PathVariable UUID stepId) {
    procedureStepService.removeStep(stepId);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Edit a document requirement already on a DRAFT version (brief §23)")
  @PatchMapping("/{code}/versions/{versionNumber}/documents/{documentId}")
  public AdminProcedureVersionDetailResponse updateDocument(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @PathVariable UUID documentId,
      @Valid @RequestBody UpdateDocumentRequirementRequest request) {
    documentRequirementService.updateRequirement(
        documentId,
        request.name(),
        request.description(),
        request.requirementType(),
        request.requiredByDefault(),
        request.numberOfCopies(),
        request.originalRequired(),
        request.copyRequired(),
        request.translationRequired(),
        request.swornTranslationRequired(),
        request.apostilleRequired(),
        request.legalisationRequired(),
        request.validityPeriodDescription(),
        request.notes(),
        request.sortOrder());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Remove a document requirement from a DRAFT version (brief §23)")
  @DeleteMapping("/{code}/versions/{versionNumber}/documents/{documentId}")
  public AdminProcedureVersionDetailResponse removeDocument(
      @PathVariable String code, @PathVariable int versionNumber, @PathVariable UUID documentId) {
    documentRequirementService.removeRequirement(documentId);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Add a fee to a DRAFT version (brief §25)")
  @PostMapping("/{code}/versions/{versionNumber}/fees")
  public ResponseEntity<AdminProcedureVersionDetailResponse> addFee(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AddFeeRequest request) {
    ProcedureVersion version = version(code, versionNumber);
    feeService.addFee(
        version, request.stableCode(), request.feeType(), request.amount(), request.currency());
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(version(code, versionNumber)));
  }

  @Operation(summary = "Edit a fee already on a DRAFT version (brief §25)")
  @PatchMapping("/{code}/versions/{versionNumber}/fees/{feeId}")
  public AdminProcedureVersionDetailResponse updateFee(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @PathVariable UUID feeId,
      @Valid @RequestBody UpdateFeeRequest request) {
    feeService.updateFee(
        feeId,
        request.amount(),
        request.currency(),
        request.description(),
        request.paymentInstructions(),
        request.refundable());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Remove a fee from a DRAFT version (brief §25)")
  @DeleteMapping("/{code}/versions/{versionNumber}/fees/{feeId}")
  public AdminProcedureVersionDetailResponse removeFee(
      @PathVariable String code, @PathVariable int versionNumber, @PathVariable UUID feeId) {
    feeService.removeFee(feeId);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Submit a DRAFT version for review (brief §22)")
  @PostMapping("/{code}/versions/{versionNumber}/submit")
  public AdminProcedureVersionDetailResponse submit(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.submitForReview(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Approve a version under review (brief §22, self-approval blocked)")
  @PostMapping("/{code}/versions/{versionNumber}/approve")
  public AdminProcedureVersionDetailResponse approve(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody(required = false) ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.approve(
        version.getId(), actor(principal), request != null ? request.comment() : null);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Request changes on a version under review, sending it back to DRAFT")
  @PostMapping("/{code}/versions/{versionNumber}/request-changes")
  public AdminProcedureVersionDetailResponse requestChanges(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.requestChanges(version.getId(), actor(principal), request.comment());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Publish an APPROVED version")
  @PostMapping("/{code}/versions/{versionNumber}/publish")
  public AdminProcedureVersionDetailResponse publish(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody PublishRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.publish(version.getId(), actor(principal), request.effectiveFrom());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Archive a PUBLISHED version")
  @PostMapping("/{code}/versions/{versionNumber}/archive")
  public AdminProcedureVersionDetailResponse archive(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    procedureAdminService.archive(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Publish-readiness checks, without publishing (brief §42/§91)")
  @GetMapping("/{code}/versions/{versionNumber}/validate")
  public ValidationResponse validate(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate effectiveFrom) {
    ProcedureVersion version = version(code, versionNumber);
    return ValidationResponse.from(
        procedurePublishingService.readiness(version.getId(), effectiveFrom));
  }

  @Operation(summary = "Structural diff between two versions (brief §68)")
  @GetMapping("/{code}/diff")
  public ProcedureVersionDiffResponse diff(
      @PathVariable String code, @RequestParam int from, @RequestParam int to) {
    ProcedureVersion fromVersion = version(code, from);
    ProcedureVersion toVersion = version(code, to);
    return diffService.diff(fromVersion.getId(), toVersion.getId());
  }

  @Operation(summary = "Active user cases still depending on this version (brief §72/§73)")
  @GetMapping("/{code}/versions/{versionNumber}/impact")
  public ProcedureVersionImpactResponse impact(
      @PathVariable String code, @PathVariable int versionNumber) {
    ProcedureVersion version = version(code, versionNumber);
    return new ProcedureVersionImpactResponse(
        userCaseSnapshotRevisionRepository.countActiveCasesOnProcedureVersion(version.getId()));
  }

  @Operation(summary = "Review history for a version (brief §65/§112)")
  @GetMapping("/{code}/versions/{versionNumber}/reviews")
  public List<AdminReviewResponse> reviews(
      @PathVariable String code, @PathVariable int versionNumber) {
    ProcedureVersion version = version(code, versionNumber);
    return reviewCoordinator.history(AuditEntityType.PROCEDURE_VERSION, version.getId()).stream()
        .map(AdminReviewResponse::from)
        .toList();
  }

  private AdminProcedureVersionDetailResponse detailOf(ProcedureVersion version) {
    return AdminProcedureVersionDetailResponse.from(
        version,
        procedureStepService.listForVersion(version.getId()),
        documentRequirementService.listForVersion(version.getId()),
        feeService.listForVersion(version.getId()),
        procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()));
  }

  private ProcedureVersion version(String code, int versionNumber) {
    Procedure procedure = procedureService.getByCode(code);
    return procedureVersionRepository
        .findByProcedure_IdAndVersionNumberFetchingActors(procedure.getId(), versionNumber)
        .orElseThrow(
            () ->
                new com.foreignerwarsaw.procedure.core.ProcedureVersionNotFoundException(
                    code, versionNumber));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
