package com.foreignerwarsaw.procedure.admin;

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
 * CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN responsibility split. No Angular admin UI exists for any of
 * this (brief §92) - Phase 9's job.
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

  public ProcedureAdminController(
      ProcedureService procedureService,
      ProcedureVersionService procedureVersionService,
      ProcedurePublishingService procedurePublishingService,
      ProcedureStepService procedureStepService,
      DocumentRequirementService documentRequirementService,
      DocumentTypeRepository documentTypeRepository,
      OfficialSourceService officialSourceService,
      SourceVerificationService sourceVerificationService,
      UserAccountService userAccountService) {
    this.procedureService = procedureService;
    this.procedureVersionService = procedureVersionService;
    this.procedurePublishingService = procedurePublishingService;
    this.procedureStepService = procedureStepService;
    this.documentRequirementService = documentRequirementService;
    this.documentTypeRepository = documentTypeRepository;
    this.officialSourceService = officialSourceService;
    this.sourceVerificationService = sourceVerificationService;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "Create a new procedure identity (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures")
  public ResponseEntity<String> createProcedure(
      @Valid @RequestBody CreateProcedureRequest request) {
    Procedure procedure =
        procedureService.createProcedure(
            request.code(),
            request.categoryCode(),
            request.canonicalName(),
            request.shortDescription(),
            request.jurisdictionScope());
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
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Add a step to a DRAFT version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/steps")
  public ResponseEntity<StepResponse> addStep(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AddStepRequest request) {
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
    return ResponseEntity.status(HttpStatus.CREATED).body(StepResponse.from(step));
  }

  @Operation(summary = "Add a document requirement to a DRAFT version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/documents")
  public ResponseEntity<DocumentRequirementResponse> addDocument(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AddDocumentRequirementRequest request) {
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
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(DocumentRequirementResponse.from(document));
  }

  @Operation(summary = "Create an official source (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/sources")
  public ResponseEntity<OfficialSourceAdminResponse> createSource(
      @Valid @RequestBody CreateOfficialSourceRequest request) {
    OfficialSource source =
        officialSourceService.create(request.title(), request.sourceUrl(), request.sourceType());
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
    return ResponseEntity.ok(OfficialSourceAdminResponse.from(officialSourceService.getById(id)));
  }

  @Operation(summary = "Attach an official source to a version (CONTENT_EDITOR/ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/sources")
  public ResponseEntity<Void> attachSource(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AttachSourceRequest request) {
    ProcedureVersion version = version(code, versionNumber);
    OfficialSource source = officialSourceService.getById(request.officialSourceId());
    procedureVersionService.attachSource(version, source, request.role());
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
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  @Operation(summary = "Archive a published version (ADMIN)")
  @PostMapping("/procedures/{code}/versions/{versionNumber}/archive")
  public ResponseEntity<ProcedureVersionAdminResponse> archive(
      @PathVariable String code, @PathVariable int versionNumber) {
    ProcedureVersion version =
        procedurePublishingService.archive(version(code, versionNumber).getId());
    return ResponseEntity.ok(ProcedureVersionAdminResponse.from(version));
  }

  private ProcedureVersion version(String code, int versionNumber) {
    Procedure procedure = procedureService.getByCode(code);
    return procedureVersionService.getByProcedureAndVersionNumber(procedure, versionNumber);
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
