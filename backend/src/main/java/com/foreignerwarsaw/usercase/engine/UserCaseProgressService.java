package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentApplicability;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic progress calculation (brief §19/§20/§87/§128) - documented formula, no hidden
 * weighting, no single misleading percentage.
 */
@Component
public class UserCaseProgressService {

  public CaseProgress calculate(List<UserCaseStep> steps, List<UserCaseDocument> documents) {
    List<UserCaseStep> mandatorySteps =
        steps.stream()
            .filter(s -> s.isMandatory() && s.getStatus() != UserCaseStepStatus.NOT_APPLICABLE)
            .toList();
    int stepsTotal = mandatorySteps.size();
    int stepsCompleted =
        (int)
            mandatorySteps.stream()
                .filter(s -> s.getStatus() == UserCaseStepStatus.COMPLETED)
                .count();

    List<UserCaseDocument> mandatoryDocuments =
        documents.stream().filter(UserCaseDocument::isMandatory).toList();
    int documentsTotal = mandatoryDocuments.size();
    int documentsReady =
        (int)
            mandatoryDocuments.stream()
                .filter(d -> d.getStatus() == UserCaseDocumentStatus.READY)
                .count();

    int conditionalToReview =
        (int)
            documents.stream()
                .filter(
                    d -> d.getApplicability() == UserCaseDocumentApplicability.NEEDS_CONFIRMATION)
                .count();

    return new CaseProgress(
        stepsCompleted, stepsTotal, documentsReady, documentsTotal, conditionalToReview);
  }
}
