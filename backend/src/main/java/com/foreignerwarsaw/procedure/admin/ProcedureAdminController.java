package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.procedure.admin.dto.AddDocumentRequirementRequest;
import com.foreignerwarsaw.procedure.admin.dto.AddStepRequest;
import com.foreignerwarsaw.procedure.admin.dto.AttachSourceRequest;
import com.foreignerwarsaw.procedure.admin.dto.CreateDraftVersionRequest;
import com.foreignerwarsaw.procedure.admin.dto.CreateOfficialSourceRequest;
import com.foreignerwarsaw.procedure.admin.dto.CreateProcedureRequest;
import com.foreignerwarsaw.procedure.admin.dto.OfficialSourceAdminResponse;
import com.foreignerwarsaw.procedure.admin.dto.ProcedureVersionAdminResponse;
import com.foreignerwarsaw.procedure.admin.dto.PublishRequest;
import com.foreignerwarsaw.procedure.admin.dto.RecordVerificationRequest;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedurePublishingService;
import com.foreignerwarsaw.procedure.core.ProcedureService;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionService;
import com.foreignerwarsaw.procedure.core.dto.DocumentRequirementResponse;
import com.foreignerwarsaw.procedure.core.dto.StepResponse;
import com.foreignerwarsaw.procedure.document.DocumentRequirementService;
import com.foreignerwarsaw.procedure.document.DocumentType;
import com.foreignerwarsaw.procedure.document.DocumentTypeRepository;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceService;
import com.foreignerwarsaw.procedure.source.SourceVerificationService;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import com.foreignerwarsaw.procedure.step.ProcedureStepService;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal internal content-management API (brief §43) - just enough to run the full
 * create-draft-through-publish lifecycle through the real HTTP + Spring Security stack (brief §80's
 * "one of the most important Phase 4 tests"), not a full admin surface. Role restrictions are
 * enforced by {@code SecurityConfig}'s URL-pattern matchers (this codebase's existing authorization
 * style - no {@code @PreAuthorize} precedent to break from), one matcher per specific action rather
 * than a blanket role check on the whole prefix, per brief §44's
 * CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN responsibility split.
 *
 * <p><b>Pre-Phase-10 hardening (brief §B):</b> Phase 9's {@code AuditLog} originally covered only
 * the new {@code /api/v1/admin/**} surface, leaving this older path as a silent audit gap - real
 * legal content authored through here would never appear in the administrative audit trail. Every
 * mutating action below now also calls {@link AuditService#record}, so no content-authoring path
 * bypasses the formal audit trail regardless of which prefix a caller uses (the retrofit option,
 * chosen over deprecating this controller outright, since {@code AdminProcedureController} and both
 * this and the new admin surface's own integration tests deliberately keep reusing these endpoints
 * for identity/draft/step/document/source creation - see PHASE_10_REPORT.md's hardening section).
 * The Angular Admin UI (Phase 9) itself never calls this controller for submit/approve/publish/
 * archive - it uses {@code /api/v1/admin/procedures/**} for those, which already had audit coverage
 * from Phase 9; this controller's own submit/approve/publish/archive actions are audited here too,
 * defensively, in case a future caller reaches them directly.
 */
@RestController
@RequestMapping("/api/v1/internal/content")
@Tag(name = "Internal - Content Management")
public class ProcedureAdminController {

  private final ProcedureService procedureService;
  private final ProcedureVersionService procedureVersionService;
  private final ProcedurePublishingService procedurePublishingService;
  private final ProcedureStepService procedureStepService;
  private final DocumentRequirementService documentRequirementService;
  private final DocumentTypeRepository documentTypeRepository;
  private final OfficialSourceService officialSourceService;
  private final SourceVerificationService sourceVerificationService;
  private final UserAccountService userAccountService;
  private final AuditService auditService;

  public ProcedureAdminController(
      ProcedureService procedureService,
      ProcedureVersionService procedureVersionService,
      ProcedurePublishingService procedurePublishingService,
      ProcedureStepService procedureStepService,
      DocumentRequirementService documentRequirementService,
      DocumentTypeRepository documentTypeRepository,
      OfficialSourceService officialSourceService,
      SourceVerificationService sourceVerificationService,
      UserAccountService userAccountService,
      AuditService auditService) {
    this.procedureService = procedureService;
    this.procedureVersionService = procedureVersionService;
    this.procedurePublishingService = procedurePublishingService;
    this.procedureStepService = procedureStepService;
    this.documentRequirementService = documentRequirementService;
    this.documentTypeRepository = documentTypeRepository;
    this.officialSourceService = officialSourceService;
    this.sourceVerificationService = sourceVerificationService;
    this.userAccountService = userAccountService;
    this.auditService = auditService;
  }

  @Operation(summary = "Create a new procedure identity (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures")
  public ResponseEntity<String> createProcedure(
      @Valid @RequestBody CreateProcedureRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    Procedure procedure =
        procedureService.createProcedure(
            request.code(),
            request.categoryCode(),
            request.canonicalName(),
            request.shortDescription(),
            request.jurisdictionScope());
    auditService.record(
        actor(principal),
        AuditActionType.PROCEDURE_CREATED,
        AuditEntityType.PROCEDURE,
        procedure.getId(),
        procedure.getCode(),
        null,
        "Created procedure " + procedure.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(procedure.getCode());
  }

  @Operation(summary = "Create a DRAFT version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions")
  public ResponseEntity<ProcedureVersionAdminResponse> createDraftVersion(
      @PathVariable String code,
      @Valid @RequestBody CreateDraftVersionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    Procedure procedure = procedureService.getByCode(code);
    ProcedureVersion version =
        procedureVersionService.createDraft(
            procedure, request.title(), request.summary(), request.description(), actor(principal));
    audit(
        actor(principal),
        AuditActionType.PROCEDURE_VERSION_CREATED,
        version,
        "Created draft version");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Add a step to a DRAFT version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/steps")
  public ResponseEntity<StepResponse> addStep(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AddStepRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    var step =
        procedureStepService.addStep(
            version,
            request.stableCode(),
            request.title(),
            request.description(),
            request.stepType(),
            request.sortOrder(),
            request.mandatory());
    audit(
        actor(principal),
        AuditActionType.PROCEDURE_STEP_ADDED,
        version,
        "Added step " + request.stableCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(StepResponse.from(step));
  }

  @Operation(summary = "Add a document requirement to a DRAFT version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/documents")
  public ResponseEntity<DocumentRequirementResponse> addDocument(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AddDocumentRequirementRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    DocumentType documentType =
        request.documentTypeCode() != null
            ? documentTypeRepository.findByCodeIgnoreCase(request.documentTypeCode()).orElse(null)
            : null;
    var document =
        documentRequirementService.addRequirement(
            version,
            request.stableCode(),
            documentType,
            request.name(),
            request.description(),
            request.requirementType(),
            request.requiredByDefault(),
            request.sortOrder());
    audit(
        actor(principal),
        AuditActionType.PROCEDURE_DOCUMENT_ADDED,
        version,
        "Added document " + request.stableCode());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(DocumentRequirementResponse.from(document));
  }

  @Operation(summary = "Create an official source (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/sources")
  public ResponseEntity<OfficialSourceAdminResponse> createSource(
      @Valid @RequestBody CreateOfficialSourceRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    OfficialSource source =
        officialSourceService.create(request.title(), request.sourceUrl(), request.sourceType());
    auditService.record(
        actor(principal),
        AuditActionType.SOURCE_CREATED,
        AuditEntityType.OFFICIAL_SOURCE,
        source.getId(),
        null,
        null,
        "Created source " + source.getTitle());
    return ResponseEntity.status(HttpStatus.CREATED).body(OfficialSourceAdminResponse.from(source));
  }

  @Operation(summary = "Record a source verification outcome (LEGAL_REVIEWER/ADMIN)")
  @PostMapping("/sources/{id}/verify")
  public ResponseEntity<OfficialSourceAdminResponse> verifySource(
      @PathVariable UUID id,
      @Valid @RequestBody RecordVerificationRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    sourceVerificationService.recordVerification(
        id, actor(principal), request.status(), request.notes());
    OfficialSource source = officialSourceService.getById(id);
    auditService.record(
        actor(principal),
        request.status() == VerificationStatus.OUTDATED
            ? AuditActionType.SOURCE_MARKED_OUTDATED
            : AuditActionType.SOURCE_VERIFIED,
        AuditEntityType.OFFICIAL_SOURCE,
        source.getId(),
        null,
        null,
        "Recorded verification " + request.status() + " for source " + source.getTitle());
    return ResponseEntity.ok(OfficialSourceAdminResponse.from(source));
  }

  @Operation(summary = "Attach an official source to a version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/sources")
  public ResponseEntity<Void> attachSource(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AttachSourceRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version = version(code, versionNumber);
    OfficialSource source = officialSourceService.getById(request.officialSourceId());
    procedureVersionService.attachSource(version, source, request.role());
    audit(
        actor(principal),
        AuditActionType.PROCEDURE_VERSION_UPDATED,
        version,
        "Attached source " + source.getTitle() + " (" + request.role() + ")");
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Submit a DRAFT version for review (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/submit")
  public ResponseEntity<ProcedureVersionAdminResponse> submit(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version =
        procedureVersionService.submitForReview(
            version(code, versionNumber).getId(), actor(principal));
    audit(
        actor(principal),
        AuditActionType.CONTENT_SUBMITTED,
        version,
        "Submitted for review (legacy endpoint)");
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Approve a version under review (LEGAL_REVIEWER/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/approve")
  public ResponseEntity<ProcedureVersionAdminResponse> approve(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version =
        procedureVersionService.approve(version(code, versionNumber).getId(), actor(principal));
    audit(
        actor(principal), AuditActionType.CONTENT_APPROVED, version, "Approved (legacy endpoint)");
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Publish an approved version (ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/publish")
  public ResponseEntity<ProcedureVersionAdminResponse> publish(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody PublishRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version =
        procedurePublishingService.publish(
            version(code, versionNumber).getId(), actor(principal), request.effectiveFrom());
    audit(
        actor(principal),
        AuditActionType.CONTENT_PUBLISHED,
        version,
        "Published effective " + request.effectiveFrom() + " (legacy endpoint)");
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Archive a published version (ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/archive")
  public ResponseEntity<ProcedureVersionAdminResponse> archive(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ProcedureVersion version =
        procedurePublishingService.archive(version(code, versionNumber).getId());
    audit(
        actor(principal), AuditActionType.CONTENT_ARCHIVED, version, "Archived (legacy endpoint)");
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  private ProcedureVersion version(String code, int versionNumber) {
    Procedure procedure = procedureService.getByCode(code);
    return procedureVersionService.getByProcedureAndVersionNumber(procedure, versionNumber);
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }

  private void audit(User actor, AuditActionType type, ProcedureVersion version, String summary) {
    auditService.record(
        actor,
        type,
        AuditEntityType.PROCEDURE_VERSION,
        version.getId(),
        version.getProcedure().getCode(),
        version.getId(),
        summary);
  }
}
