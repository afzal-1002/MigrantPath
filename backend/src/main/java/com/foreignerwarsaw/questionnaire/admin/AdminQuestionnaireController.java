package com.foreignerwarsaw.questionnaire.admin;

import com.foreignerwarsaw.admin.dto.AdminReviewResponse;
import com.foreignerwarsaw.admin.dto.ImpactCountResponse;
import com.foreignerwarsaw.admin.dto.ReviewDecisionRequest;
import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.admin.dto.PublishRequest;
import com.foreignerwarsaw.questionnaire.admin.dto.AdminQuestionnaireSummaryResponse;
import com.foreignerwarsaw.questionnaire.admin.dto.AdminQuestionnaireVersionDetailResponse;
import com.foreignerwarsaw.questionnaire.admin.dto.CreateQuestionnaireDraftRequest;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentRepository;
import com.foreignerwarsaw.questionnaire.core.Questionnaire;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireRepository;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersion;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersionRepository;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersionService;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestionRepository;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
 * Phase 9's Questionnaire admin surface (brief §49-§54/§56) - version lifecycle and read-only
 * question listing only; deep question/dependency editing through this API is a deliberate scope
 * cut, documented in PHASE_9_REPORT.md's Deviations - the existing seed content already defines the
 * question structure a copied draft inherits, and a full drag/drop dependency builder was judged
 * disproportionate for this phase (brief §53's own "if strict... complexity" allowance).
 */
@RestController
@RequestMapping("/api/v1/admin/questionnaires")
@Tag(name = "Admin - Questionnaires")
public class AdminQuestionnaireController {

  private final QuestionnaireRepository questionnaireRepository;
  private final QuestionnaireVersionRepository questionnaireVersionRepository;
  private final QuestionnaireVersionService questionnaireVersionService;
  private final QuestionnaireQuestionRepository questionnaireQuestionRepository;
  private final QuestionnaireAdminService questionnaireAdminService;
  private final ContentReviewCoordinator reviewCoordinator;
  private final AssessmentRepository assessmentRepository;
  private final UserAccountService userAccountService;

  public AdminQuestionnaireController(
      QuestionnaireRepository questionnaireRepository,
      QuestionnaireVersionRepository questionnaireVersionRepository,
      QuestionnaireVersionService questionnaireVersionService,
      QuestionnaireQuestionRepository questionnaireQuestionRepository,
      QuestionnaireAdminService questionnaireAdminService,
      ContentReviewCoordinator reviewCoordinator,
      AssessmentRepository assessmentRepository,
      UserAccountService userAccountService) {
    this.questionnaireRepository = questionnaireRepository;
    this.questionnaireVersionRepository = questionnaireVersionRepository;
    this.questionnaireVersionService = questionnaireVersionService;
    this.questionnaireQuestionRepository = questionnaireQuestionRepository;
    this.questionnaireAdminService = questionnaireAdminService;
    this.reviewCoordinator = reviewCoordinator;
    this.assessmentRepository = assessmentRepository;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "List questionnaires with their active/latest version summary")
  @GetMapping
  public List<AdminQuestionnaireSummaryResponse> list() {
    return questionnaireRepository.findAll().stream()
        .map(
            q -> {
              List<QuestionnaireVersion> all = questionnaireVersionService.listVersions(q.getId());
              QuestionnaireVersion active =
                  questionnaireVersionRepository
                      .findActivePublishedVersion(q.getCode(), LocalDate.now())
                      .orElse(null);
              QuestionnaireVersion latest = all.isEmpty() ? null : all.get(0);
              return AdminQuestionnaireSummaryResponse.from(q, active, latest);
            })
        .toList();
  }

  @Operation(summary = "Every version of one questionnaire, newest first")
  @GetMapping("/{code}")
  public List<AdminQuestionnaireVersionDetailResponse> versionHistory(@PathVariable String code) {
    Questionnaire questionnaire = getQuestionnaire(code);
    return questionnaireVersionService.listVersions(questionnaire.getId()).stream()
        .map(
            v ->
                AdminQuestionnaireVersionDetailResponse.from(
                    v,
                    questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
                        v.getId())))
        .toList();
  }

  @Operation(summary = "Create a new draft copied from a published/draft version")
  @PostMapping("/{code}/versions/{versionNumber}/copy")
  public ResponseEntity<AdminQuestionnaireVersionDetailResponse> copyVersion(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody CreateQuestionnaireDraftRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion source = version(code, versionNumber);
    QuestionnaireVersion copy =
        questionnaireAdminService.createDraftFrom(
            source, request.title(), request.description(), actor(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(detailOf(copy));
  }

  @Operation(summary = "Submit a DRAFT version for review")
  @PostMapping("/{code}/versions/{versionNumber}/submit")
  public AdminQuestionnaireVersionDetailResponse submit(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion version = version(code, versionNumber);
    questionnaireAdminService.submitForReview(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Approve a version under review (self-approval blocked)")
  @PostMapping("/{code}/versions/{versionNumber}/approve")
  public AdminQuestionnaireVersionDetailResponse approve(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody(required = false) ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion version = version(code, versionNumber);
    questionnaireAdminService.approve(
        version.getId(), actor(principal), request != null ? request.comment() : null);
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Request changes, sending a version back to DRAFT")
  @PostMapping("/{code}/versions/{versionNumber}/request-changes")
  public AdminQuestionnaireVersionDetailResponse requestChanges(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @RequestBody ReviewDecisionRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion version = version(code, versionNumber);
    questionnaireAdminService.requestChanges(version.getId(), actor(principal), request.comment());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Publish an APPROVED version")
  @PostMapping("/{code}/versions/{versionNumber}/publish")
  public AdminQuestionnaireVersionDetailResponse publish(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @Valid @RequestBody PublishRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion version = version(code, versionNumber);
    questionnaireAdminService.publish(version.getId(), actor(principal), request.effectiveFrom());
    return detailOf(version(code, versionNumber));
  }

  @Operation(summary = "Archive a PUBLISHED version")
  @PostMapping("/{code}/versions/{versionNumber}/archive")
  public AdminQuestionnaireVersionDetailResponse archive(
      @PathVariable String code,
      @PathVariable int versionNumber,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    QuestionnaireVersion version = version(code, versionNumber);
    questionnaireAdminService.archive(version.getId(), actor(principal));
    return detailOf(version(code, versionNumber));
  }

  @Operation(
      summary = "In-progress/completed assessments permanently bound to this version (brief §72)")
  @GetMapping("/{code}/versions/{versionNumber}/impact")
  public ImpactCountResponse impact(@PathVariable String code, @PathVariable int versionNumber) {
    QuestionnaireVersion version = version(code, versionNumber);
    return new ImpactCountResponse(
        assessmentRepository.countByQuestionnaireVersion_Id(version.getId()),
        "Assessments bound to this version");
  }

  @Operation(summary = "Review history for a version")
  @GetMapping("/{code}/versions/{versionNumber}/reviews")
  public List<AdminReviewResponse> reviews(
      @PathVariable String code, @PathVariable int versionNumber) {
    QuestionnaireVersion version = version(code, versionNumber);
    return reviewCoordinator
        .history(AuditEntityType.QUESTIONNAIRE_VERSION, version.getId())
        .stream()
        .map(AdminReviewResponse::from)
        .toList();
  }

  private AdminQuestionnaireVersionDetailResponse detailOf(QuestionnaireVersion version) {
    return AdminQuestionnaireVersionDetailResponse.from(
        version,
        questionnaireQuestionRepository.findByQuestionnaireVersion_IdOrderBySortOrder(
            version.getId()));
  }

  private QuestionnaireVersion version(String code, int versionNumber) {
    Questionnaire questionnaire = getQuestionnaire(code);
    return questionnaireVersionRepository
        .findByQuestionnaire_IdAndVersionNumberFetchingActors(questionnaire.getId(), versionNumber)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "QUESTIONNAIRE_VERSION_NOT_FOUND",
                    "No version " + versionNumber + " found for questionnaire " + code));
  }

  private Questionnaire getQuestionnaire(String code) {
    return questionnaireRepository
        .findByCode(code)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "QUESTIONNAIRE_NOT_FOUND",
                    "No questionnaire found for code " + code));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
