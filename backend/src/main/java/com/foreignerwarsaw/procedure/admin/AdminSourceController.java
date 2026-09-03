package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.procedure.admin.dto.AdminSourceDetailResponse;
import com.foreignerwarsaw.procedure.admin.dto.CreateOfficialSourceRequest;
import com.foreignerwarsaw.procedure.admin.dto.RecordVerificationRequest;
import com.foreignerwarsaw.procedure.admin.dto.SourceUsageResponse;
import com.foreignerwarsaw.procedure.admin.dto.SourceVerificationResponse;
import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.OfficialSourceService;
import com.foreignerwarsaw.procedure.source.SourceVerificationRepository;
import com.foreignerwarsaw.procedure.source.SourceVerificationService;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersionSourceRepository;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's Official Source admin surface (brief §28-§35/§56) - list/detail/impact/verification-
 * history are new; create and verify are also exposed here (in addition to their original {@code
 * /api/v1/internal/content/sources/**} home from Phase 4) so every content type's admin surface is
 * reachable under one coherent {@code /api/v1/admin/**} prefix (brief §79), without touching the
 * already-tested Phase 4 routes.
 */
@RestController
@RequestMapping("/api/v1/admin/sources")
@Tag(name = "Admin - Sources")
public class AdminSourceController {

  private final OfficialSourceRepository officialSourceRepository;
  private final OfficialSourceService officialSourceService;
  private final SourceVerificationService sourceVerificationService;
  private final SourceVerificationRepository sourceVerificationRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final ThresholdVersionSourceRepository thresholdVersionSourceRepository;
  private final UserAccountService userAccountService;

  public AdminSourceController(
      OfficialSourceRepository officialSourceRepository,
      OfficialSourceService officialSourceService,
      SourceVerificationService sourceVerificationService,
      SourceVerificationRepository sourceVerificationRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      ThresholdVersionSourceRepository thresholdVersionSourceRepository,
      UserAccountService userAccountService) {
    this.officialSourceRepository = officialSourceRepository;
    this.officialSourceService = officialSourceService;
    this.sourceVerificationService = sourceVerificationService;
    this.sourceVerificationRepository = sourceVerificationRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.thresholdVersionSourceRepository = thresholdVersionSourceRepository;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "List official sources")
  @GetMapping
  public List<AdminSourceDetailResponse> list() {
    return officialSourceRepository.findAll().stream()
        .map(AdminSourceDetailResponse::from)
        .toList();
  }

  @Operation(summary = "One source's detail")
  @GetMapping("/{id}")
  public AdminSourceDetailResponse detail(@PathVariable UUID id) {
    return AdminSourceDetailResponse.from(officialSourceService.getById(id));
  }

  @Operation(summary = "Create an official source")
  @PostMapping
  public ResponseEntity<AdminSourceDetailResponse> create(
      @Valid @RequestBody CreateOfficialSourceRequest request) {
    OfficialSource source =
        officialSourceService.create(request.title(), request.sourceUrl(), request.sourceType());
    return ResponseEntity.status(HttpStatus.CREATED).body(AdminSourceDetailResponse.from(source));
  }

  @Operation(
      summary =
          "Record a verification outcome (brief §30) - VERIFIED, NEEDS_REVIEW, or OUTDATED"
              + " (brief §34's \"mark outdated\" workflow: reuse this action with status=OUTDATED"
              + " and a reason in notes)")
  @PostMapping("/{id}/verify")
  public AdminSourceDetailResponse verify(
      @PathVariable UUID id,
      @Valid @RequestBody RecordVerificationRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    sourceVerificationService.recordVerification(
        id, actor(principal), request.status(), request.notes());
    return AdminSourceDetailResponse.from(officialSourceService.getById(id));
  }

  @Operation(summary = "Verification history for a source (brief §30)")
  @GetMapping("/{id}/verifications")
  public List<SourceVerificationResponse> verifications(@PathVariable UUID id) {
    return sourceVerificationRepository.findByOfficialSource_IdOrderByCheckedAtDesc(id).stream()
        .map(SourceVerificationResponse::from)
        .toList();
  }

  @Operation(summary = "What published content depends on this source (brief §33/§34)")
  @GetMapping("/{id}/usage")
  public SourceUsageResponse usage(@PathVariable UUID id) {
    return new SourceUsageResponse(
        procedureVersionSourceRepository.countByOfficialSource_Id(id),
        ruleVersionSourceRepository.countByOfficialSource_Id(id),
        thresholdVersionSourceRepository.countByOfficialSource_Id(id));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
