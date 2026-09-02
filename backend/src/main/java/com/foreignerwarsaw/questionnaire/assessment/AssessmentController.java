package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.questionnaire.assessment.dto.AnswerRequest;
import com.foreignerwarsaw.questionnaire.assessment.dto.AssessmentDetailResponse;
import com.foreignerwarsaw.questionnaire.assessment.dto.AssessmentSummaryResponse;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireCodes;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint requires an authenticated session (SecurityConfig's default {@code
 * anyRequest().authenticated()} - no anonymous assessment support in Phase 5, brief §31/§32). Every
 * id-scoped endpoint enforces ownership via {@link AssessmentService#getOwned} (brief §57) - a 404,
 * never a 403, for another user's assessment id, so the response never confirms the id even exists
 * (brief §57's IDOR requirement).
 */
@RestController
@RequestMapping("/api/v1/assessments")
@Tag(name = "Assessments")
public class AssessmentController {

  private final AssessmentService assessmentService;
  private final AssessmentAnswerService assessmentAnswerService;
  private final AssessmentCompletionService assessmentCompletionService;
  private final AssessmentQueryService assessmentQueryService;
  private final UserAccountService userAccountService;

  public AssessmentController(
      AssessmentService assessmentService,
      AssessmentAnswerService assessmentAnswerService,
      AssessmentCompletionService assessmentCompletionService,
      AssessmentQueryService assessmentQueryService,
      UserAccountService userAccountService) {
    this.assessmentService = assessmentService;
    this.assessmentAnswerService = assessmentAnswerService;
    this.assessmentCompletionService = assessmentCompletionService;
    this.assessmentQueryService = assessmentQueryService;
    this.userAccountService = userAccountService;
  }

  @Operation(
      summary =
          "Start the Warsaw general assessment, or resume the caller's existing in-progress one (brief §33)")
  @PostMapping
  public AssessmentDetailResponse start(@AuthenticationPrincipal AppUserPrincipal principal) {
    Assessment started =
        assessmentService.start(
            userAccountService.getById(principal.getUserId()),
            QuestionnaireCodes.WARSAW_GENERAL_ASSESSMENT);
    return assessmentQueryService.toDetailResponse(reload(started, principal));
  }

  @Operation(summary = "The caller's own assessments, most recent first (brief §56)")
  @GetMapping
  public List<AssessmentSummaryResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
    return assessmentService.listForUser(principal.getUserId()).stream()
        .map(AssessmentSummaryResponse::from)
        .toList();
  }

  @Operation(summary = "One assessment's current visible questions, answers, and progress")
  @GetMapping("/{id}")
  public AssessmentDetailResponse get(
      @PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
    return assessmentQueryService.toDetailResponse(
        assessmentService.getOwned(id, principal.getUserId()));
  }

  @Operation(summary = "Save (or change) the answer to one currently-visible question")
  @PutMapping("/{id}/answers/{questionCode}")
  public AssessmentDetailResponse saveAnswer(
      @PathVariable UUID id,
      @PathVariable String questionCode,
      @RequestBody AnswerRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    // Ownership check only - AssessmentAnswerService re-fetches by id inside its own transaction
    // rather than mutating this (about-to-be-detached) reference; see its Javadoc.
    assessmentService.getOwned(id, principal.getUserId());
    assessmentAnswerService.saveAnswer(id, questionCode, request);
    return assessmentQueryService.toDetailResponse(
        assessmentService.getOwned(id, principal.getUserId()));
  }

  @Operation(
      summary =
          "Complete the assessment (fails with the list of missing required questions if not ready, brief §36)")
  @PostMapping("/{id}/complete")
  public AssessmentDetailResponse complete(
      @PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
    assessmentService.getOwned(id, principal.getUserId());
    assessmentCompletionService.complete(id);
    return assessmentQueryService.toDetailResponse(
        assessmentService.getOwned(id, principal.getUserId()));
  }

  @Operation(
      summary =
          "Restart an in-progress assessment (blank slate) or start a fresh editable assessment from a completed one's answers (brief §35/§36)")
  @PostMapping("/{id}/restart")
  public AssessmentDetailResponse restart(
      @PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal) {
    Assessment next = assessmentService.restart(id, principal.getUserId());
    return assessmentQueryService.toDetailResponse(reload(next, principal));
  }

  /**
   * Every mutating endpoint above re-fetches by id before assembling the response DTO, so {@code
   * AssessmentQueryService} always operates on a freshly-loaded, fully-fetched entity within its
   * own transaction rather than a possibly lazily-uninitialized one returned from a prior
   * transaction.
   */
  private Assessment reload(Assessment assessment, AppUserPrincipal principal) {
    return assessmentService.getOwned(assessment.getId(), principal.getUserId());
  }
}
