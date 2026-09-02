package com.foreignerwarsaw.questionnaire.core;

import com.foreignerwarsaw.questionnaire.core.dto.QuestionnaireStructureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requires authentication like every other non-public path (SecurityConfig's default {@code
 * anyRequest().authenticated()} - brief §31's "questionnaire metadata could be public" is
 * deliberately not taken here, since Phase 5 as a whole is authenticated-only end to end, brief
 * §32). Never returns an eligibility determination (brief §90) - only "what is this questionnaire,"
 * mirroring {@code ProcedureController}'s own scope limit.
 */
@RestController
@RequestMapping("/api/v1/questionnaires")
@Tag(name = "Questionnaires")
public class QuestionnaireController {

  private final QuestionnaireQueryService questionnaireQueryService;

  public QuestionnaireController(QuestionnaireQueryService questionnaireQueryService) {
    this.questionnaireQueryService = questionnaireQueryService;
  }

  @Operation(
      summary = "The currently active version of the Warsaw general assessment questionnaire")
  @GetMapping("/active")
  public QuestionnaireStructureResponse active() {
    return questionnaireQueryService.getActiveStructure(
        QuestionnaireCodes.WARSAW_GENERAL_ASSESSMENT);
  }
}
