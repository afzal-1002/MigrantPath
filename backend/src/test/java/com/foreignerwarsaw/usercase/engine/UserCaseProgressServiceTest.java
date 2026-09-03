package com.foreignerwarsaw.usercase.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.procedure.document.RequirementType;
import com.foreignerwarsaw.procedure.step.StepType;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentApplicability;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Deterministic progress formula (brief §19/§20/§87/§88, docs/cases/USER_CASE_MODEL.md) - two
 * transparent counts, never one blended percentage.
 */
class UserCaseProgressServiceTest {

  private final UserCaseProgressService service = new UserCaseProgressService();
  private final Instant now = Instant.parse("2026-09-03T00:00:00Z");

  private UserCaseStep step(String code, boolean mandatory, UserCaseStepStatus status) {
    UserCaseStep step =
        new UserCaseStep(
            null,
            null,
            null,
            code,
            "Title " + code,
            null,
            null,
            StepType.PREPARATION,
            0,
            mandatory,
            now);
    if (status != UserCaseStepStatus.NOT_STARTED) {
      step.changeStatus(status, now);
    }
    return step;
  }

  private UserCaseDocument document(
      String code,
      RequirementType type,
      UserCaseDocumentApplicability applicability,
      UserCaseDocumentStatus status) {
    boolean mandatory = type == RequirementType.DEFAULT_REQUIRED;
    UserCaseDocument document =
        new UserCaseDocument(
            null,
            null,
            null,
            code,
            "Name " + code,
            null,
            type,
            applicability,
            mandatory,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            now);
    if (status != document.getStatus()) {
      document.changeStatus(status, now);
    }
    return document;
  }

  @Test
  void countsOnlyMandatoryApplicableStepsInTheDenominator() {
    List<UserCaseStep> steps =
        List.of(
            step("S1", true, UserCaseStepStatus.COMPLETED),
            step("S2", true, UserCaseStepStatus.NOT_STARTED),
            step(
                "S3", false, UserCaseStepStatus.COMPLETED)); // optional - excluded from denominator

    CaseProgress progress = service.calculate(steps, List.of());

    assertThat(progress.stepsTotal()).isEqualTo(2);
    assertThat(progress.stepsCompleted()).isEqualTo(1);
  }

  @Test
  void countsOnlyMandatoryDefaultRequiredDocumentsInTheDenominator() {
    List<UserCaseDocument> documents =
        List.of(
            document(
                "D1",
                RequirementType.DEFAULT_REQUIRED,
                UserCaseDocumentApplicability.APPLICABLE,
                UserCaseDocumentStatus.READY),
            document(
                "D2",
                RequirementType.DEFAULT_REQUIRED,
                UserCaseDocumentApplicability.APPLICABLE,
                UserCaseDocumentStatus.NOT_STARTED),
            document(
                "D3",
                RequirementType.INFORMATIONAL,
                UserCaseDocumentApplicability.APPLICABLE,
                UserCaseDocumentStatus.READY));

    CaseProgress progress = service.calculate(List.of(), documents);

    assertThat(progress.documentsTotal()).isEqualTo(2);
    assertThat(progress.documentsReady()).isEqualTo(1);
  }

  @Test
  void conditionalDocumentsAreCountedSeparately_neverInTheMainDenominator() {
    List<UserCaseDocument> documents =
        List.of(
            document(
                "D1",
                RequirementType.DEFAULT_REQUIRED,
                UserCaseDocumentApplicability.APPLICABLE,
                UserCaseDocumentStatus.READY),
            document(
                "D2",
                RequirementType.CONDITIONAL,
                UserCaseDocumentApplicability.NEEDS_CONFIRMATION,
                UserCaseDocumentStatus.NOT_STARTED));

    CaseProgress progress = service.calculate(List.of(), documents);

    assertThat(progress.documentsTotal()).isEqualTo(1);
    assertThat(progress.documentsReady()).isEqualTo(1);
    assertThat(progress.conditionalDocumentsToReview()).isEqualTo(1);
  }

  @Test
  void aStepMarkedNotApplicableIsExcludedFromTheDenominatorEvenIfMandatory() {
    UserCaseStep applicableStep = step("S1", true, UserCaseStepStatus.NOT_STARTED);
    // NOT_APPLICABLE can only be reached via restoreStatus (a future engine's own path) - not
    // through the public changeStatus API, matching how Phase 8 itself never sets it.
    UserCaseStep notApplicableStep = step("S2", true, UserCaseStepStatus.NOT_STARTED);
    notApplicableStep.restoreStatus(UserCaseStepStatus.NOT_APPLICABLE, null, now);

    CaseProgress progress =
        service.calculate(List.of(applicableStep, notApplicableStep), List.of());

    assertThat(progress.stepsTotal()).isEqualTo(1);
  }

  @Test
  void emptyListsProduceZeroDenominators_neverADivideByZeroOrFabricatedNumber() {
    CaseProgress progress = service.calculate(List.of(), List.of());
    assertThat(progress).isEqualTo(new CaseProgress(0, 0, 0, 0, 0));
  }
}
