package com.foreignerwarsaw.rules.evaluation;

import com.foreignerwarsaw.questionnaire.assessment.Assessment;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentFactsService;
import com.foreignerwarsaw.questionnaire.assessment.AssessmentService;
import com.foreignerwarsaw.user.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one read-only, authenticated, ownership-checked entry point into the rules engine (brief
 * §90's "no full admin CRUD API" - deliberately mirroring {@code ThresholdService}'s Phase 4
 * precedent of a controller-less engine; this is the one read surface Phase 6 does add, since
 * unlike Threshold there is now real seeded content for a caller to evaluate against). Ownership is
 * enforced via {@link AssessmentService#getOwned} exactly like every other {@code
 * /api/v1/assessments/{id}/...} endpoint (brief §57): a 404, never a 403, for another user's
 * assessment id.
 *
 * <p>Returns the machine-readable {@link RuleEvaluationBundle} straight from the engine -
 * deliberately not a recommendation, ranking, or {@code PRIMARY_MATCH}/{@code POSSIBLE_ALTERNATIVE}
 * (brief §79/§90, Phase 7's job). Admin/authoring endpoints for creating and publishing {@code
 * Rule}/{@code RuleVersion} content are out of scope for this phase for the same reason {@code
 * ThresholdService} shipped with none in Phase 4 - no content exists yet for an admin to manage
 * through a dedicated UI/API.
 */
@RestController
@RequestMapping("/api/v1/assessments/{id}/rule-evaluations")
@Tag(name = "Rule Evaluations")
public class RuleEvaluationController {

  private final AssessmentService assessmentService;
  private final AssessmentFactsService assessmentFactsService;
  private final RuleEvaluationService ruleEvaluationService;
  private final Clock clock;

  public RuleEvaluationController(
      AssessmentService assessmentService,
      AssessmentFactsService assessmentFactsService,
      RuleEvaluationService ruleEvaluationService,
      Clock clock) {
    this.assessmentService = assessmentService;
    this.assessmentFactsService = assessmentFactsService;
    this.ruleEvaluationService = ruleEvaluationService;
    this.clock = clock;
  }

  @Operation(
      summary =
          "Every active rule's result for the caller's own assessment (brief §39/§78), on a given"
              + " or today's evaluation date")
  @GetMapping
  public RuleEvaluationBundle evaluate(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate evaluationDate,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    Assessment assessment = assessmentService.getOwned(id, principal.getUserId());
    LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now(clock);
    return ruleEvaluationService.evaluateApplicableRules(
        assessmentFactsService.buildFacts(assessment), date);
  }
}
