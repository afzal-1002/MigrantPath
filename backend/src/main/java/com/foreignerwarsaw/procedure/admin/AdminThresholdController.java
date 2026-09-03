package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.admin.dto.AdminReviewResponse;
import com.foreignerwarsaw.admin.dto.ReviewDecisionRequest;
import com.foreignerwarsaw.admin.dto.ValidationResponse;
import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.procedure.admin.dto.AdminThresholdSummaryResponse;
import com.foreignerwarsaw.procedure.admin.dto.AdminThresholdVersionResponse;
import com.foreignerwarsaw.procedure.admin.dto.AttachSourceRequest;
import com.foreignerwarsaw.procedure.admin.dto.CreateThresholdRequest;
import com.foreignerwarsaw.procedure.admin.dto.PublishRequest;
import com.foreignerwarsaw.procedure.admin.dto.ThresholdImpactResponse;
import com.foreignerwarsaw.procedure.admin.dto.UpdateThresholdVersionRequest;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceService;
import com.foreignerwarsaw.procedure.threshold.Threshold;
import com.foreignerwarsaw.procedure.threshold.ThresholdService;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersion;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersionSourceRepository;
import com.foreignerwarsaw.rules.core.RuleThresholdReferenceRepository;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's Threshold admin surface (brief §46-§48/§56) - the first HTTP-reachable admin API this
 * engine has ever had (Phase 4 deliberately shipped none, since no threshold content existed yet to
 * manage - see {@code ThresholdService}'s own Javadoc). Pre-Phase-10 hardening (brief §D) added
 * {@code attachSource}/source display - a threshold version can no longer publish without one, so
 * the admin editor needs a way to attach one, mirroring {@code AdminRuleController}.
 */
@RestController
@RequestMapping("/api/v1/admin/thresholds")
@Tag(name = "Admin - Thresholds")
public class AdminThresholdController {

  private final ThresholdService thresholdService;
  private final ThresholdAdminService thresholdAdminService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final RuleThresholdReferenceRepository ruleThresholdReferenceRepository;
  private final ThresholdVersionSourceRepository thresholdVersionSourceRepository;
  private final OfficialSourceService officialSourceService;
  private final UserAccountService userAccountService;

  public AdminThresholdController(
      ThresholdService thresholdService,
      ThresholdAdminService thresholdAdminService,
      ContentReviewCoordinator reviewCoordinator,
      RuleThresholdReferenceRepository ruleThresholdReferenceRepository,
      ThresholdVersionSourceRepository thresholdVersionSourceRepository,
      OfficialSourceService officialSourceService,
      UserAccountService userAccountService) {
    this.thresholdService = thresholdService;
    this.thresholdAdminService = thresholdAdminService;
    this.reviewCoordinator = reviewCoordinator;
    this.ruleThresholdReferenceRepository = ruleThresholdReferenceRepository;
    this.thresholdVersionSourceRepository = thresholdVersionSourceRepository;
    this.officialSourceService = officialSourceService;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "List thresholds")
  @GetMapping
  public List<AdminThresholdSummaryResponse> list() {
    return thresholdService.listAll().stream().map(AdminThresholdSummaryResponse::from).toList();
  }

  @Operation(summary = "Every version of one threshold")
  @GetMapping("/{code}")
  public List<AdminThresholdVersionResponse> versions(@PathVariable String code) {
    Threshold threshold = thresholdService.getByCode(code);
    return thresholdService.listVersions(threshold.getId()).stream().map(this::detailOf).toList();
  }

  @Operation(summary = "Which Rules reference this threshold (brief §48)")
  @GetMapping("/{code}/impact")
  public ThresholdImpactResponse impact(@PathVariable String code) {
    List<String> ruleCodes =
        ruleThresholdReferenceRepository.findByThresholdCodeFetchingRule(code).stream()
            .map(
                r ->
                    r.getRuleVersion().getRule().getCode()
                        + " v"
                        + r.getRuleVersion().getVersionNumber())
            .distinct()
            .toList();
    return new ThresholdImpactResponse(ruleCodes);
  }

  @Operation(summary = "Create a threshold identity")
  @PostMapping
  public ResponseEntity<String> createThreshold(
      @Valid @RequestBody CreateThresholdRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    Threshold threshold =
        thresholdAdminService.createThreshold(
            request.code(), request.canonicalName(), request.valueType(), actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(threshold.getCode());
  }

  @Operation(summary = "Create a DRAFT version")
  @PostMapping("/{code}/versions")
  public ResponseEntity<AdminThresholdVersionResponse> createDraftVersion(
      @PathVariable String code,
      @Valid @RequestBody UpdateThresholdVersionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ThresholdVersion version =
        thresholdAdminService.createDraftVersion(
            code, request.value(), request.valueText(), actor(principal));
    if (request.effectiveFrom() != null || request.notes() != null) {
      version =
          thresholdAdminService.updateDraft(
              version.getId(),
              request.value(),
              request.valueText(),
              request.effectiveFrom(),
              request.notes(),
              actor(principal));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(version));
  }

  @Operation(summary = "Edit a DRAFT version's value/dates/notes")
  @PatchMapping("/{code}/versions/{versionId}")
  public AdminThresholdVersionResponse updateDraft(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @Valid @RequestBody UpdateThresholdVersionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    ThresholdVersion version =
        thresholdAdminService.updateDraft(
            versionId,
            request.value(),
            request.valueText(),
            request.effectiveFrom(),
            request.notes(),
            actor(principal));
    return detailOf(version);
  }

  @Operation(summary = "Attach an official source to a version (brief §D)")
  @PostMapping("/{code}/versions/{versionId}/sources")
  public AdminThresholdVersionResponse attachSource(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @Valid @RequestBody AttachSourceRequest request) {
    ThresholdVersion version = thresholdService.getVersionById(versionId);
    OfficialSource source = officialSourceService.getById(request.officialSourceId());
    thresholdService.attachSource(version, source, request.role());
    return detailOf(thresholdService.getVersionById(versionId));
  }

  @Operation(summary = "Submit a DRAFT version for review")
  @PostMapping("/{code}/versions/{versionId}/submit")
  public AdminThresholdVersionResponse submit(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return detailOf(thresholdAdminService.submitForReview(versionId, actor(principal)));
  }

  @Operation(summary = "Approve a version under review (self-approval blocked)")
  @PostMapping("/{code}/versions/{versionId}/approve")
  public AdminThresholdVersionResponse approve(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @RequestBody(required = false) ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return detailOf(
        thresholdAdminService.approve(
            versionId, actor(principal), request != null ? request.comment() : null));
  }

  @Operation(summary = "Request changes, sending a version back to DRAFT")
  @PostMapping("/{code}/versions/{versionId}/request-changes")
  public AdminThresholdVersionResponse requestChanges(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @RequestBody ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return detailOf(
        thresholdAdminService.requestChanges(versionId, actor(principal), request.comment()));
  }

  @Operation(summary = "Publish an APPROVED version")
  @PostMapping("/{code}/versions/{versionId}/publish")
  public AdminThresholdVersionResponse publish(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @Valid @RequestBody PublishRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return detailOf(
        thresholdAdminService.publish(versionId, actor(principal), request.effectiveFrom()));
  }

  @Operation(summary = "Archive a PUBLISHED version")
  @PostMapping("/{code}/versions/{versionId}/archive")
  public AdminThresholdVersionResponse archive(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return detailOf(thresholdAdminService.archive(versionId, actor(principal)));
  }

  @Operation(summary = "Publish-readiness checks, without publishing")
  @GetMapping("/{code}/versions/{versionId}/validate")
  public ValidationResponse validate(
      @PathVariable String code,
      @PathVariable UUID versionId,
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate effectiveFrom) {
    return ValidationResponse.from(thresholdService.readiness(versionId, effectiveFrom));
  }

  @Operation(summary = "Review history for a version")
  @GetMapping("/{code}/versions/{versionId}/reviews")
  public List<AdminReviewResponse> reviews(
      @PathVariable String code, @PathVariable UUID versionId) {
    return reviewCoordinator.history(AuditEntityType.THRESHOLD_VERSION, versionId).stream()
        .map(AdminReviewResponse::from)
        .toList();
  }

  private AdminThresholdVersionResponse detailOf(ThresholdVersion version) {
    return AdminThresholdVersionResponse.from(
        version,
        thresholdVersionSourceRepository.findByThresholdVersion_IdFetchingSource(version.getId()));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
