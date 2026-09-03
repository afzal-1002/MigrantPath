package com.foreignerwarsaw.admin;

import com.foreignerwarsaw.admin.dto.AdminDashboardResponse;
import com.foreignerwarsaw.admin.review.ContentReviewCoordinator;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersionRepository;
import com.foreignerwarsaw.questionnaire.core.QuestionnaireVersionRepository;
import com.foreignerwarsaw.rules.core.RuleVersionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 9's admin dashboard summary (brief §16). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin - Dashboard")
public class AdminDashboardController {

  private final ProcedureVersionRepository procedureVersionRepository;
  private final RuleVersionRepository ruleVersionRepository;
  private final ThresholdVersionRepository thresholdVersionRepository;
  private final QuestionnaireVersionRepository questionnaireVersionRepository;
  private final OfficialSourceRepository officialSourceRepository;
  private final ContentReviewCoordinator reviewCoordinator;

  public AdminDashboardController(
      ProcedureVersionRepository procedureVersionRepository,
      RuleVersionRepository ruleVersionRepository,
      ThresholdVersionRepository thresholdVersionRepository,
      QuestionnaireVersionRepository questionnaireVersionRepository,
      OfficialSourceRepository officialSourceRepository,
      ContentReviewCoordinator reviewCoordinator) {
    this.procedureVersionRepository = procedureVersionRepository;
    this.ruleVersionRepository = ruleVersionRepository;
    this.thresholdVersionRepository = thresholdVersionRepository;
    this.questionnaireVersionRepository = questionnaireVersionRepository;
    this.officialSourceRepository = officialSourceRepository;
    this.reviewCoordinator = reviewCoordinator;
  }

  @Operation(summary = "Operational summary counts (brief §16)")
  @GetMapping
  public AdminDashboardResponse dashboard() {
    return new AdminDashboardResponse(
        procedureVersionRepository.countByStatus(PublicationStatus.DRAFT),
        ruleVersionRepository.countByStatus(PublicationStatus.DRAFT),
        thresholdVersionRepository.countByStatus(PublicationStatus.DRAFT),
        questionnaireVersionRepository.countByStatus(PublicationStatus.DRAFT),
        reviewCoordinator.pendingQueue().size(),
        procedureVersionRepository.countByStatus(PublicationStatus.APPROVED),
        ruleVersionRepository.countByStatus(PublicationStatus.APPROVED),
        thresholdVersionRepository.countByStatus(PublicationStatus.APPROVED),
        questionnaireVersionRepository.countByStatus(PublicationStatus.APPROVED),
        officialSourceRepository.countByVerificationStatus(VerificationStatus.NEEDS_REVIEW)
            + officialSourceRepository.countByVerificationStatus(VerificationStatus.DRAFT),
        officialSourceRepository.countByVerificationStatus(VerificationStatus.OUTDATED));
  }
}
