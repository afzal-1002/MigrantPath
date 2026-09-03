package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.UserAccountService;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentStatus;
import com.foreignerwarsaw.usercase.core.UserCaseFeeStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStepStatus;
import com.foreignerwarsaw.usercase.engine.dto.CaseDetailResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseDocumentUpdateRequest;
import com.foreignerwarsaw.usercase.engine.dto.CaseEventResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseFeeUpdateRequest;
import com.foreignerwarsaw.usercase.engine.dto.CaseStatusUpdateRequest;
import com.foreignerwarsaw.usercase.engine.dto.CaseStepUpdateRequest;
import com.foreignerwarsaw.usercase.engine.dto.CaseSummaryResponse;
import com.foreignerwarsaw.usercase.engine.dto.RequirementChangeReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint requires authentication and enforces case ownership (brief §54/§55/§107) - a 404,
 * never a 403, for another user's case, exactly like every other owned resource in this codebase.
 * No admin/support access exists to another user's case (brief §55: privacy first).
 */
@RestController
@Tag(name = "User Cases")
public class UserCaseController {

  private final CaseCreationValidator creationValidator;
  private final UserCaseCreationService creationService;
  private final UserCaseQueryService queryService;
  private final UserCaseAccessService accessService;
  private final UserCaseStatusService statusService;
  private final UserCaseItemService itemService;
  private final CaseRequirementChangeService changeService;
  private final UserCaseUpgradeService upgradeService;
  private final UserAccountService userAccountService;

  public UserCaseController(
      CaseCreationValidator creationValidator,
      UserCaseCreationService creationService,
      UserCaseQueryService queryService,
      UserCaseAccessService accessService,
      UserCaseStatusService statusService,
      UserCaseItemService itemService,
      CaseRequirementChangeService changeService,
      UserCaseUpgradeService upgradeService,
      UserAccountService userAccountService) {
    this.creationValidator = creationValidator;
    this.creationService = creationService;
    this.queryService = queryService;
    this.accessService = accessService;
    this.statusService = statusService;
    this.itemService = itemService;
    this.changeService = changeService;
    this.upgradeService = upgradeService;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "Start tracking a recommended pathway as a personal case (brief §4/§52)")
  @PostMapping("/api/v1/recommendations/{recommendationId}/cases")
  public CaseDetailResponse create(
      @PathVariable UUID recommendationId, @AuthenticationPrincipal AppUserPrincipal principal) {
    // Idempotency first (brief §77): a recommendation that already has a case returns it
    // directly, even if that recommendation's pinned ProcedureVersion has since gone stale -
    // staleness only blocks creating a *new* case, never returning to one that exists. Ownership
    // is still enforced below via getDetail's own access check.
    var existing = creationService.findExistingCase(recommendationId);
    if (existing.isPresent()) {
      return queryService.getDetail(existing.get().getId(), principal.getUserId());
    }

    var user = userAccountService.getById(principal.getUserId());
    var validated = creationValidator.validate(recommendationId, principal.getUserId());
    UserCase userCase = creationService.createFromRecommendation(validated, user);
    return queryService.getDetail(userCase.getId(), principal.getUserId());
  }

  @Operation(summary = "The caller's own cases, most recently updated first (brief §40)")
  @GetMapping("/api/v1/cases")
  public List<CaseSummaryResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
    return queryService.listForUser(principal.getUserId());
  }

  @Operation(summary = "One case's full detail (brief §41/§58)")
  @GetMapping("/api/v1/cases/{caseId}")
  public CaseDetailResponse get(
      @PathVariable UUID caseId, @AuthenticationPrincipal AppUserPrincipal principal) {
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(summary = "Change the case's own status (brief §22/§59)")
  @PatchMapping("/api/v1/cases/{caseId}/status")
  public CaseDetailResponse updateStatus(
      @PathVariable UUID caseId,
      @RequestBody CaseStatusUpdateRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    var user = userAccountService.getById(principal.getUserId());
    statusService.changeStatus(
        caseId, principal.getUserId(), parseEnum(UserCaseStatus.class, request.status()), user);
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(summary = "Update one step's checklist status (brief §9/§60)")
  @PatchMapping("/api/v1/cases/{caseId}/steps/{stepId}")
  public CaseDetailResponse updateStep(
      @PathVariable UUID caseId,
      @PathVariable UUID stepId,
      @RequestBody CaseStepUpdateRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    var user = userAccountService.getById(principal.getUserId());
    itemService.updateStepStatus(
        caseId,
        principal.getUserId(),
        stepId,
        parseEnum(UserCaseStepStatus.class, request.status()),
        user);
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(
      summary = "Update one document's checklist status and/or personal note (brief §11/§37/§61)")
  @PatchMapping("/api/v1/cases/{caseId}/documents/{documentId}")
  public CaseDetailResponse updateDocument(
      @PathVariable UUID caseId,
      @PathVariable UUID documentId,
      @RequestBody CaseDocumentUpdateRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    var user = userAccountService.getById(principal.getUserId());
    if (request.status() != null) {
      itemService.updateDocumentStatus(
          caseId,
          principal.getUserId(),
          documentId,
          parseEnum(UserCaseDocumentStatus.class, request.status()),
          user);
    }
    if (request.userNote() != null) {
      itemService.updateDocumentNote(
          caseId, principal.getUserId(), documentId, request.userNote(), user);
    }
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(summary = "Update one fee's manual payment-tracking status (brief §15)")
  @PatchMapping("/api/v1/cases/{caseId}/fees/{feeId}")
  public CaseDetailResponse updateFee(
      @PathVariable UUID caseId,
      @PathVariable UUID feeId,
      @RequestBody CaseFeeUpdateRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    var user = userAccountService.getById(principal.getUserId());
    itemService.updateFeeStatus(
        caseId,
        principal.getUserId(),
        feeId,
        parseEnum(UserCaseFeeStatus.class, request.status()),
        user);
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(
      summary =
          "What changed between this case's snapshot and the currently active procedure content (brief §30)")
  @GetMapping("/api/v1/cases/{caseId}/requirement-changes")
  public RequirementChangeReportResponse requirementChanges(
      @PathVariable UUID caseId, @AuthenticationPrincipal AppUserPrincipal principal) {
    UserCase userCase = accessService.getOwned(caseId, principal.getUserId());
    return RequirementChangeReportResponse.from(changeService.detectChanges(userCase));
  }

  @Operation(
      summary =
          "Explicitly upgrade this case to the currently active procedure content (brief §31/§32)")
  @PostMapping("/api/v1/cases/{caseId}/upgrade")
  public CaseDetailResponse upgrade(
      @PathVariable UUID caseId, @AuthenticationPrincipal AppUserPrincipal principal) {
    var user = userAccountService.getById(principal.getUserId());
    upgradeService.upgrade(caseId, principal.getUserId(), user);
    return queryService.getDetail(caseId, principal.getUserId());
  }

  @Operation(summary = "This case's append-only activity timeline (brief §24/§82)")
  @GetMapping("/api/v1/cases/{caseId}/events")
  public List<CaseEventResponse> events(
      @PathVariable UUID caseId, @AuthenticationPrincipal AppUserPrincipal principal) {
    return queryService.getEvents(caseId, principal.getUserId());
  }

  private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "CASE_ITEM_TRANSITION_INVALID", "Unknown status value: " + value);
    }
  }
}
