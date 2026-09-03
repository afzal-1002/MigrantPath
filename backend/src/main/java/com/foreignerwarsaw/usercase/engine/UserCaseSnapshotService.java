package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersionRepository;
import com.foreignerwarsaw.procedure.document.RequirementType;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
import com.foreignerwarsaw.procedure.fee.FeeVersionRepository;
import com.foreignerwarsaw.procedure.step.StepVersion;
import com.foreignerwarsaw.procedure.step.StepVersionRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.usercase.core.SnapshotRevisionReason;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentApplicability;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseFee;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevision;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevisionRepository;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds one immutable {@link UserCaseSnapshotRevision} from one {@link ProcedureVersion} (brief
 * §68) - the one place Phase 8 reads Phase 4 content and turns it into personal checklist rows.
 * Shared by case creation (revision 1) and upgrade (revision N+1, brief §31) so the two can never
 * diverge in how a snapshot is built.
 *
 * <p><b>No conditional/rule-based personalization exists</b> (brief §70-73): every step and fee on
 * the {@link ProcedureVersion} is snapshotted as applicable (Phase 4's schema has no
 * conditional-step or conditional-fee concept to honor); every document's {@link
 * UserCaseDocumentApplicability} is derived directly, deterministically, from its {@link
 * RequirementType} - never from a raw {@code AssessmentFacts} answer read here (brief §70's
 * explicit "BAD: if (married) addMarriageCertificate()" is never done - see
 * docs/cases/USER_CASE_MODEL.md's "Personalization" section for the full reasoning, including why
 * Phase 6's {@code DOCUMENT_REQUIREMENT}/{@code STEP} rule targets aren't used yet).
 */
@Service
public class UserCaseSnapshotService {

  private final StepVersionRepository stepVersionRepository;
  private final DocumentRequirementVersionRepository documentRequirementVersionRepository;
  private final FeeVersionRepository feeVersionRepository;
  private final UserCaseSnapshotRevisionRepository revisionRepository;
  private final UserCaseStepRepository userCaseStepRepository;
  private final UserCaseDocumentRepository userCaseDocumentRepository;
  private final UserCaseFeeRepository userCaseFeeRepository;

  public UserCaseSnapshotService(
      StepVersionRepository stepVersionRepository,
      DocumentRequirementVersionRepository documentRequirementVersionRepository,
      FeeVersionRepository feeVersionRepository,
      UserCaseSnapshotRevisionRepository revisionRepository,
      UserCaseStepRepository userCaseStepRepository,
      UserCaseDocumentRepository userCaseDocumentRepository,
      UserCaseFeeRepository userCaseFeeRepository) {
    this.stepVersionRepository = stepVersionRepository;
    this.documentRequirementVersionRepository = documentRequirementVersionRepository;
    this.feeVersionRepository = feeVersionRepository;
    this.revisionRepository = revisionRepository;
    this.userCaseStepRepository = userCaseStepRepository;
    this.userCaseDocumentRepository = userCaseDocumentRepository;
    this.userCaseFeeRepository = userCaseFeeRepository;
  }

  @Transactional
  public UserCaseSnapshotRevision buildRevision(
      UserCase userCase,
      int revisionNumber,
      ProcedureVersion procedureVersion,
      LocalDate evaluationDate,
      SnapshotRevisionReason reason,
      User actor,
      UserCaseSnapshotRevision previousRevision,
      Instant now) {
    UserCaseSnapshotRevision revision =
        revisionRepository.save(
            UserCaseSnapshotRevision.create(
                userCase,
                revisionNumber,
                procedureVersion,
                evaluationDate,
                reason,
                actor,
                previousRevision,
                now));

    for (StepVersion stepVersion :
        stepVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(
            procedureVersion.getId())) {
      userCaseStepRepository.save(
          new UserCaseStep(
              revision,
              stepVersion.getProcedureStep(),
              stepVersion,
              stepVersion.getProcedureStep().getStableCode(),
              stepVersion.getTitle(),
              stepVersion.getDescription(),
              stepVersion.getDetailedInstructions(),
              stepVersion.getStepType(),
              stepVersion.getSortOrder(),
              stepVersion.isMandatory(),
              now));
    }

    for (DocumentRequirementVersion docVersion :
        documentRequirementVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(
            procedureVersion.getId())) {
      UserCaseDocumentApplicability applicability =
          applicabilityFor(docVersion.getRequirementType());
      boolean mandatory =
          docVersion.getRequirementType() == RequirementType.DEFAULT_REQUIRED
              && docVersion.isRequiredByDefault();
      userCaseDocumentRepository.save(
          new UserCaseDocument(
              revision,
              docVersion.getDocumentRequirement(),
              docVersion,
              docVersion.getDocumentRequirement().getStableCode(),
              docVersion.getName(),
              docVersion.getDescription(),
              docVersion.getRequirementType(),
              applicability,
              mandatory,
              docVersion.getNumberOfCopies(),
              docVersion.getOriginalRequired(),
              docVersion.getTranslationRequired(),
              docVersion.getSwornTranslationRequired(),
              docVersion.getApostilleRequired(),
              docVersion.getLegalisationRequired(),
              docVersion.getValidityPeriodDescription(),
              docVersion.getNotes(),
              docVersion.getSortOrder(),
              now));
    }

    int feeSortOrder = 0;
    for (FeeVersion feeVersion :
        feeVersionRepository.findByProcedureVersion_Id(procedureVersion.getId())) {
      userCaseFeeRepository.save(
          new UserCaseFee(
              revision,
              feeVersion.getFee(),
              feeVersion,
              feeVersion.getFee().getStableCode(),
              feeVersion.getFee().getFeeType(),
              feeVersion.getAmount(),
              feeVersion.getCurrency(),
              feeVersion.getDescription(),
              feeVersion.getPaymentInstructions(),
              feeSortOrder++,
              now));
    }

    return revision;
  }

  /**
   * Brief §12/§13/§74: {@code CONDITIONAL} becomes {@code NEEDS_CONFIRMATION}, never a fabricated
   * {@code NOT_APPLICABLE} - nothing in Phase 8 has actually evaluated whether it applies to this
   * specific user.
   */
  private UserCaseDocumentApplicability applicabilityFor(RequirementType requirementType) {
    return switch (requirementType) {
      case DEFAULT_REQUIRED, INFORMATIONAL -> UserCaseDocumentApplicability.APPLICABLE;
      case CONDITIONAL -> UserCaseDocumentApplicability.NEEDS_CONFIRMATION;
    };
  }
}
