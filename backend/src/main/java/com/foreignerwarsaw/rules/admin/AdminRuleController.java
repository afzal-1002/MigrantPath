package com.foreignerwarsaw.rules.admin;

import com.foreignerwarsaw.admin.dto.AdminReviewResponse;
import com.foreignerwarsaw.admin.dto.ReviewDecisionRequest;
import com.foreignerwarsaw.admin.dto.ValidationResponse;
import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.admin.validation.ValidationIssue;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.procedure.admin.dto.AttachSourceRequest;
import com.foreignerwarsaw.procedure.admin.dto.PublishRequest;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.OfficialSourceService;
import com.foreignerwarsaw.procedure.source.SourceVerificationService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFacts;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentStatus;
import com.foreignerwarsaw.rules.admin.dto.AdminRuleSummaryResponse;
import com.foreignerwarsaw.rules.admin.dto.AdminRuleVersionDetailResponse;
import com.foreignerwarsaw.rules.admin.dto.CreateRuleDraftVersionRequest;
import com.foreignerwarsaw.rules.admin.dto.CreateRuleRequest;
import com.foreignerwarsaw.rules.admin.dto.RuleDryRunRequest;
import com.foreignerwarsaw.rules.admin.dto.RuleVersionDiffResponse;
import com.foreignerwarsaw.rules.admin.dto.UpdateRuleDraftRequest;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidationException;
import com.foreignerwarsaw.rules.condition.ConditionTreeValidator;
import com.foreignerwarsaw.rules.core.Rule;
import com.foreignerwarsaw.rules.core.RulePublishingService;
import com.foreignerwarsaw.rules.core.RuleRepository;
import com.foreignerwarsaw.rules.core.RuleService;
import com.foreignerwarsaw.rules.core.RuleVersion;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import com.foreignerwarsaw.rules.core.RuleVersionService;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluator;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 9's Rule admin surface (brief §36-§45/§56) - mirrors {@code AdminProcedureController}. */
@RestController
@RequestMapping("/api/v1/admin/rules")
@Tag(name = "Admin - Rules")
public class AdminRuleController {

  private final RuleService ruleService;
  private final RuleRepository ruleRepository;
  private final RuleVersionService ruleVersionService;
  private final RuleVersionRepository ruleVersionRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final RulePublishingService rulePublishingService;
  private final RuleAdminService ruleAdminService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final OfficialSourceService officialSourceService;
  private final SourceVerificationService sourceVerificationService;
  private final ConditionTreeValidator conditionTreeValidator;
  private final RuleEvaluator ruleEvaluator;
  private final UserAccountService userAccountService;

  public AdminRuleController(
      RuleService ruleService,
      RuleRepository ruleRepository,
      RuleVersionService ruleVersionService,
      RuleVersionRepository ruleVersionRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      RulePublishingService rulePublishingService,
      RuleAdminService ruleAdminService,
      ContentReviewCoordinator reviewCoordinator,
      OfficialSourceService officialSourceService,
      SourceVerificationService sourceVerificationService,
      ConditionTreeValidator conditionTreeValidator,
      RuleEvaluator ruleEvaluator,
      UserAccountService userAccountService) {
    this.ruleService = ruleService;
    this.ruleRepository = ruleRepository;
    this.ruleVersionService = ruleVersionService;
    this.ruleVersionRepository = ruleVersionRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.rulePublishingService = rulePublishingService;
    this.ruleAdminService = ruleAdminService;
    this.reviewCoordinator = reviewCoordinator;
    this.officialSourceService = officialSourceService;
    this.sourceVerificationService = sourceVerificationService;
    this.conditionTreeValidator = conditionTreeValidator;
    this.ruleEvaluator = ruleEvaluator;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "List rules with their active/latest version summary")
  @GetMapping
  public List<AdminRuleSummaryResponse> list() {
    LocalDate today = LocalDate.now();
    return ruleRepository.findAll().stream()
        .map(
            r -> {
              RuleVersion active =
                  ruleVersionRepository.findActivePublishedVersion(r.getId(), today).orElse(null);
              List<RuleVersion> all = ruleVersionRepository.findByRule_Id(r.getId());
              RuleVersion latest = all.isEmpty() ? null : all.get(0);
              return AdminRuleSummaryResponse.from(r, active, latest);
            })
        .toList();
  }

  @Operation(summary = "Every version of one rule, newest first")
  @GetMapping("/{code}")
  public List<AdminRuleVersionDetailResponse> versionHistory(@PathVariable String code) {
    Rule rule = ruleService.getByCode(code);
    return ruleVersionRepository.findByRule_Id(rule.getId()).stream()
        .map(
            v ->
                AdminRuleVersionDetailResponse.from(
                    v, ruleVersionSourceRepository.findByRuleVersion_Id(v.getId())))
        .toList();
  }

  @Operation(summary = "Create a rule identity")
  @PostMapping
  public ResponseEntity<String> createRule(
      @Valid @RequestBody CreateRuleRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    Rule rule =
        ruleAdminService.createRule(
            request.code(),
            request.canonicalName(),
            request.ruleType(),
            request.targetType(),
            request.targetCode(),
            actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(rule.getCode());
  }

  @Operation(summary = "Create a DRAFT version")
  @PostMapping("/{code}/versions")
  public ResponseEntity<AdminRuleVersionDetailResponse> createDraftVersion(
      @PathVariable String code,
      @Valid @RequestBody CreateRuleDraftVersionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version =
        ruleAdminService.createDraftVersion(
            code, request.conditionTree(), request.explanationKey(), actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(version));
  }

  @Operation(summary = "Create a new draft copied from this version")
  @PostMapping("/{code}/versions/{versionNumber}/copy")
  public ResponseEntity<AdminRuleVersionDetailResponse> copyVersion(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion source = version(code, versionNumber);
    RuleVersion copy = ruleAdminService.createDraftFrom(source.getId(), actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(copy));
  }

  @Operation(summary = "Edit a DRAFT version's condition tree")
  @PatchMapping("/{code}/versions/{versionNumber}")
  public AdminRuleVersionDetailResponse updateDraft(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody UpdateRuleDraftRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.updateDraft(
        version.getId(),
        request.conditionTree(),
        request.explanationKey(),
        request.changeSummary(),
        actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Attach an official source to a version")
  @PostMapping("/{code}/versions/{versionNumber}/sources")
  public AdminRuleVersionDetailResponse attachSource(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody AttachSourceRequest request) {
    RuleVersion version = version(code, versionNumber);
    OfficialSource source = officialSourceService.getById(request.officialSourceId());
    ruleVersionService.attachSource(version, source, request.role());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Submit a DRAFT version for review")
  @PostMapping("/{code}/versions/{versionNumber}/submit")
  public AdminRuleVersionDetailResponse submit(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.submitForReview(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Approve a version under review (self-approval blocked)")
  @PostMapping("/{code}/versions/{versionNumber}/approve")
  public AdminRuleVersionDetailResponse approve(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody(required = false) ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.approve(
        version.getId(), actor(principal), request != null ? request.comment() : null);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Request changes, sending a version back to DRAFT")
  @PostMapping("/{code}/versions/{versionNumber}/request-changes")
  public AdminRuleVersionDetailResponse requestChanges(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.requestChanges(version.getId(), actor(principal), request.comment());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Publish an APPROVED version")
  @PostMapping("/{code}/versions/{versionNumber}/publish")
  public AdminRuleVersionDetailResponse publish(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody PublishRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.publish(version.getId(), actor(principal), request.effectiveFrom());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Archive a PUBLISHED version")
  @PostMapping("/{code}/versions/{versionNumber}/archive")
  public AdminRuleVersionDetailResponse archive(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    RuleVersion version = version(code, versionNumber);
    ruleAdminService.archive(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Publish-readiness checks, without publishing")
  @GetMapping("/{code}/versions/{versionNumber}/validate")
  public ValidationResponse validate(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate effectiveFrom) {
    RuleVersion version = version(code, versionNumber);
    return ValidationResponse.from(rulePublishingService.readiness(version.getId(), effectiveFrom));
  }

  @Operation(
      summary =
          "Validate an arbitrary (not-yet-saved) condition tree - used by the builder before the"
              + " first draft save")
  @PostMapping("/validate-tree")
  public ValidationResponse validateTree(@RequestBody Map<String, String> body) {
    List<ValidationIssue> issues = new ArrayList<>();
    try {
      conditionTreeValidator.validate(body.get("conditionTree"));
    } catch (ConditionTreeValidationException e) {
      e.getProblems().forEach(p -> issues.add(new ValidationIssue("CONDITION_TREE_INVALID", p)));
    }
    return ValidationResponse.from(issues);
  }

  @Operation(
      summary = "Dry-run a condition tree against synthetic facts - preview only (brief §43)")
  @PostMapping("/dry-run")
  public RuleEvaluationResult dryRun(@Valid @RequestBody RuleDryRunRequest request) {
    LocalDate evaluationDate =
        request.evaluationDate() != null ? request.evaluationDate() : LocalDate.now();
    AssessmentFacts syntheticFacts =
        new AssessmentFacts(
            null,
            null,
            null,
            "PREVIEW",
            0,
            AssessmentStatus.IN_PROGRESS,
            null,
            evaluationDate,
            request.facts() != null ? request.facts() : Map.of());
    return ruleEvaluator.previewEvaluate(
        request.conditionTree(), request.explanationKey(), syntheticFacts, evaluationDate);
  }

  @Operation(summary = "GET-based dry run for an already-saved DRAFT/IN_REVIEW/APPROVED version")
  @PostMapping("/{code}/versions/{versionNumber}/dry-run")
  public RuleEvaluationResult dryRunVersion(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody(required = false) Map<String, Object> facts,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate evaluationDate) {
    RuleVersion version = version(code, versionNumber);
    LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now();
    AssessmentFacts syntheticFacts =
        new AssessmentFacts(
            null,
            null,
            null,
            "PREVIEW",
            0,
            AssessmentStatus.IN_PROGRESS,
            null,
            date,
            facts != null ? facts : Map.of());
    return ruleEvaluator.previewEvaluate(
        version.getConditionTree(), version.getExplanationKey(), syntheticFacts, date);
  }

  @Operation(summary = "Diff between two versions of the same rule (brief §69)")
  @GetMapping("/{code}/diff")
  public RuleVersionDiffResponse diff(
      @PathVariable String code, @RequestParam int from, @RequestParam int to) {
    RuleVersion fromVersion = version(code, from);
    RuleVersion toVersion = version(code, to);
    boolean conditionChanged =
        !java.util.Objects.equals(fromVersion.getConditionTree(), toVersion.getConditionTree());
    boolean explanationChanged =
        !java.util.Objects.equals(fromVersion.getExplanationKey(), toVersion.getExplanationKey());
    return new RuleVersionDiffResponse(
        fromVersion.getId(),
        fromVersion.getVersionNumber(),
        toVersion.getId(),
        toVersion.getVersionNumber(),
        conditionChanged,
        fromVersion.getConditionTree(),
        toVersion.getConditionTree(),
        explanationChanged,
        fromVersion.getExplanationKey(),
        toVersion.getExplanationKey());
  }

  @Operation(summary = "Review history for a version")
  @GetMapping("/{code}/versions/{versionNumber}/reviews")
  public List<AdminReviewResponse> reviews(
      @PathVariable String code, @PathVariable int versionNumber) {
    RuleVersion version = version(code, versionNumber);
    return reviewCoordinator.history(AuditEntityType.RULE_VERSION, version.getId()).stream()
        .map(AdminReviewResponse::from)
        .toList();
  }

  private AdminRuleVersionDetailResponse detailOf(RuleVersion version) {
    return AdminRuleVersionDetailResponse.from(
        version, ruleVersionSourceRepository.findByRuleVersion_Id(version.getId()));
  }

  private RuleVersion version(String code, int versionNumber) {
    Rule rule = ruleService.getByCode(code);
    return ruleVersionRepository.findByRule_Id(rule.getId()).stream()
        .filter(v -> v.getVersionNumber() == versionNumber)
        .findFirst()
        .orElseThrow(
            () ->
                new com.foreignerwarsaw.common.web.ApiException(
                    HttpStatus.NOT_FOUND,
                    "RULE_VERSION_NOT_FOUND",
                    "No version " + versionNumber + " found for rule " + code));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
