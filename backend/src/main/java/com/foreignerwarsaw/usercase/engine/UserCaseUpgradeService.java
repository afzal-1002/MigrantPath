package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.usercase.core.SnapshotRevisionReason;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentStatus;
import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventType;
import com.foreignerwarsaw.usercase.core.UserCaseFee;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevision;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevisionRepository;
import com.foreignerwarsaw.usercase.core.UserCaseStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit, user-triggered upgrade to the currently active {@code ProcedureVersion} (brief §31-
 * §36) - never automatic. Creates a brand-new {@link UserCaseSnapshotRevision} (never mutates the
 * old one, brief §32/§105), then merges checklist progress forward by matching items on their
 * stable identity code: an unchanged item keeps its exact status; a previously-{@code READY}
 * document whose requirement materially changed is demoted to {@code NEEDS_UPDATE} (brief §36)
 * rather than silently staying {@code READY}; a materially-changed step resets to {@code
 * NOT_STARTED} (no separate "needs review" step status exists, unlike documents - see
 * docs/cases/REQUIREMENT_CHANGE_POLICY.md); a brand-new item starts fresh; a removed item's row
 * simply does not exist in the new revision, but remains fully queryable in the old one (brief
 * §27/§105).
 */
@Service
public class UserCaseUpgradeService {

  private final UserCaseAccessService accessService;
  private final CaseRequirementChangeService changeService;
  private final UserCaseSnapshotService snapshotService;
  private final ProcedureVersionRepository procedureVersionRepository;
  private final UserCaseSnapshotRevisionRepository revisionRepository;
  private final UserCaseStepRepository stepRepository;
  private final UserCaseDocumentRepository documentRepository;
  private final UserCaseFeeRepository feeRepository;
  private final UserCaseEventRepository eventRepository;
  private final Clock clock;

  public UserCaseUpgradeService(
      UserCaseAccessService accessService,
      CaseRequirementChangeService changeService,
      UserCaseSnapshotService snapshotService,
      ProcedureVersionRepository procedureVersionRepository,
      UserCaseSnapshotRevisionRepository revisionRepository,
      UserCaseStepRepository stepRepository,
      UserCaseDocumentRepository documentRepository,
      UserCaseFeeRepository feeRepository,
      UserCaseEventRepository eventRepository,
      Clock clock) {
    this.accessService = accessService;
    this.changeService = changeService;
    this.snapshotService = snapshotService;
    this.procedureVersionRepository = procedureVersionRepository;
    this.revisionRepository = revisionRepository;
    this.stepRepository = stepRepository;
    this.documentRepository = documentRepository;
    this.feeRepository = feeRepository;
    this.eventRepository = eventRepository;
    this.clock = clock;
  }

  @Transactional
  public UserCase upgrade(UUID caseId, UUID userId, User actor) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    if (userCase.getStatus() == UserCaseStatus.CANCELLED
        || userCase.getStatus() == UserCaseStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_STATUS_TRANSITION_INVALID",
          "This case is " + userCase.getStatus() + " and can no longer be upgraded");
    }

    RequirementChangeReport report = changeService.detectChanges(userCase);
    if (!report.newerVersionAvailable()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ALREADY_CURRENT",
          "This case is already based on the current procedure content");
    }

    ProcedureVersion newProcedureVersion =
        procedureVersionRepository
            .findByIdFetchingProcedure(report.newActiveProcedureVersionId())
            .orElseThrow();
    UserCaseSnapshotRevision oldRevision = userCase.getCurrentRevision();

    List<UserCaseStep> oldSteps =
        stepRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(oldRevision.getId());
    List<UserCaseDocument> oldDocuments =
        documentRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(oldRevision.getId());
    List<UserCaseFee> oldFees =
        feeRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(oldRevision.getId());

    int nextRevisionNumber = revisionRepository.findMaxRevisionNumber(userCase.getId()) + 1;
    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock);
    UserCaseSnapshotRevision newRevision =
        snapshotService.buildRevision(
            userCase,
            nextRevisionNumber,
            newProcedureVersion,
            today,
            SnapshotRevisionReason.UPGRADE,
            actor,
            oldRevision,
            now);

    mergeSteps(oldSteps, newRevision, now);
    mergeDocuments(oldDocuments, newRevision, now);
    mergeFees(oldFees, newRevision, now);

    userCase.attachRevision(newRevision);
    userCase.touch(now);
    eventRepository.save(
        new UserCaseEvent(
            userCase,
            UserCaseEventType.CASE_UPDATED_TO_NEW_VERSION,
            now,
            actor,
            "revision "
                + oldRevision.getRevisionNumber()
                + " -> "
                + newRevision.getRevisionNumber()));

    return userCase;
  }

  private void mergeSteps(
      List<UserCaseStep> oldSteps, UserCaseSnapshotRevision newRevision, Instant now) {
    Map<String, UserCaseStep> oldByCode =
        oldSteps.stream()
            .collect(Collectors.toMap(UserCaseStep::getStableCode, Function.identity()));
    for (UserCaseStep newStep :
        stepRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(newRevision.getId())) {
      UserCaseStep old = oldByCode.get(newStep.getStableCode());
      if (old == null) {
        continue; // brand-new item - already NOT_STARTED from the snapshot builder.
      }
      boolean materiallyChanged =
          !Objects.equals(old.getTitleSnapshot(), newStep.getTitleSnapshot())
              || !Objects.equals(old.getDescriptionSnapshot(), newStep.getDescriptionSnapshot())
              || !Objects.equals(
                  old.getDetailedInstructionsSnapshot(), newStep.getDetailedInstructionsSnapshot())
              || old.getStepType() != newStep.getStepType()
              || old.isMandatory() != newStep.isMandatory();
      if (!materiallyChanged) {
        newStep.restoreStatus(old.getStatus(), old.getCompletedAt(), now);
      }
      // else: materially changed - stays at the snapshot builder's default NOT_STARTED
      // (brief §34's conservative policy; no separate "needs review" step status exists).
    }
  }

  private void mergeDocuments(
      List<UserCaseDocument> oldDocuments, UserCaseSnapshotRevision newRevision, Instant now) {
    Map<String, UserCaseDocument> oldByCode =
        oldDocuments.stream()
            .collect(Collectors.toMap(UserCaseDocument::getStableCode, Function.identity()));
    for (UserCaseDocument newDoc :
        documentRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(newRevision.getId())) {
      UserCaseDocument old = oldByCode.get(newDoc.getStableCode());
      if (old == null) {
        continue;
      }
      boolean materiallyChanged =
          old.getRequirementType() != newDoc.getRequirementType()
              || !Objects.equals(
                  old.getNumberOfCopiesSnapshot(), newDoc.getNumberOfCopiesSnapshot())
              || !Objects.equals(
                  old.getOriginalRequiredSnapshot(), newDoc.getOriginalRequiredSnapshot())
              || !Objects.equals(
                  old.getTranslationRequiredSnapshot(), newDoc.getTranslationRequiredSnapshot())
              || !Objects.equals(
                  old.getSwornTranslationRequiredSnapshot(),
                  newDoc.getSwornTranslationRequiredSnapshot())
              || !Objects.equals(
                  old.getApostilleRequiredSnapshot(), newDoc.getApostilleRequiredSnapshot())
              || !Objects.equals(
                  old.getLegalisationRequiredSnapshot(), newDoc.getLegalisationRequiredSnapshot())
              || !Objects.equals(
                  old.getValidityPeriodDescriptionSnapshot(),
                  newDoc.getValidityPeriodDescriptionSnapshot());

      newDoc.restoreUserNote(old.getUserNote());
      if (materiallyChanged && old.getStatus() == UserCaseDocumentStatus.READY) {
        // Brief §36's exact example: a previously-READY document whose requirement changed is
        // never silently left READY.
        newDoc.markNeedsUpdate(now);
      } else if (!materiallyChanged) {
        newDoc.restoreStatus(old.getStatus(), old.getReadyAt(), now);
      }
      // else: materially changed but wasn't READY yet - stays at whatever status the snapshot
      // builder assigned (NOT_STARTED, or NOT_APPLICABLE if applicability itself changed).
    }
  }

  private void mergeFees(
      List<UserCaseFee> oldFees, UserCaseSnapshotRevision newRevision, Instant now) {
    Map<String, UserCaseFee> oldByCode =
        oldFees.stream().collect(Collectors.toMap(UserCaseFee::getStableCode, Function.identity()));
    for (UserCaseFee newFee :
        feeRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(newRevision.getId())) {
      UserCaseFee old = oldByCode.get(newFee.getStableCode());
      if (old != null) {
        newFee.restoreStatus(old.getStatus(), old.getPaidAt(), now);
      }
    }
  }
}
