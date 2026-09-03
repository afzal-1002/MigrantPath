package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionRepository;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersionRepository;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
import com.foreignerwarsaw.procedure.fee.FeeVersionRepository;
import com.foreignerwarsaw.procedure.step.StepVersion;
import com.foreignerwarsaw.procedure.step.StepVersionRepository;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseFee;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevision;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares a case's current snapshot revision against whatever {@code ProcedureVersion} is active
 * *right now* for its procedure (brief §26/§27) - read-only, never mutates the case (brief §26's
 * "do not mutate case"). Matches items by their stable identity code, never by display title (brief
 * §29). Only step/document/fee content is compared in Phase 8 - a Procedure summary/title change or
 * a source-record update alone is not detected as a separate change type (see
 * docs/cases/REQUIREMENT_CHANGE_POLICY.md's "Deviations").
 */
@Service
public class CaseRequirementChangeService {

  private final ProcedureVersionRepository procedureVersionRepository;
  private final StepVersionRepository stepVersionRepository;
  private final DocumentRequirementVersionRepository documentRequirementVersionRepository;
  private final FeeVersionRepository feeVersionRepository;
  private final UserCaseStepRepository userCaseStepRepository;
  private final UserCaseDocumentRepository userCaseDocumentRepository;
  private final UserCaseFeeRepository userCaseFeeRepository;
  private final Clock clock;

  public CaseRequirementChangeService(
      ProcedureVersionRepository procedureVersionRepository,
      StepVersionRepository stepVersionRepository,
      DocumentRequirementVersionRepository documentRequirementVersionRepository,
      FeeVersionRepository feeVersionRepository,
      UserCaseStepRepository userCaseStepRepository,
      UserCaseDocumentRepository userCaseDocumentRepository,
      UserCaseFeeRepository userCaseFeeRepository,
      Clock clock) {
    this.procedureVersionRepository = procedureVersionRepository;
    this.stepVersionRepository = stepVersionRepository;
    this.documentRequirementVersionRepository = documentRequirementVersionRepository;
    this.feeVersionRepository = feeVersionRepository;
    this.userCaseStepRepository = userCaseStepRepository;
    this.userCaseDocumentRepository = userCaseDocumentRepository;
    this.userCaseFeeRepository = userCaseFeeRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RequirementChangeReport detectChanges(UserCase userCase) {
    LocalDate today = LocalDate.now(clock);
    ProcedureVersion currentActive =
        procedureVersionRepository
            .findActivePublishedVersion(userCase.getProcedure().getId(), today)
            .orElse(null);
    UserCaseSnapshotRevision revision = userCase.getCurrentRevision();

    boolean newerVersionAvailable =
        currentActive != null
            && (revision == null
                || !currentActive.getId().equals(revision.getProcedureVersion().getId()));
    if (!newerVersionAvailable || revision == null) {
      return new RequirementChangeReport(false, null, List.of());
    }

    List<RequirementChange> changes = new ArrayList<>();
    changes.addAll(compareSteps(currentActive, revision));
    changes.addAll(compareDocuments(currentActive, revision));
    changes.addAll(compareFees(currentActive, revision));
    return new RequirementChangeReport(true, currentActive.getId(), changes);
  }

  private List<RequirementChange> compareSteps(
      ProcedureVersion currentActive, UserCaseSnapshotRevision revision) {
    Map<String, StepVersion> current =
        stepVersionRepository
            .findByProcedureVersion_IdOrderBySortOrderAsc(currentActive.getId())
            .stream()
            .collect(
                Collectors.toMap(sv -> sv.getProcedureStep().getStableCode(), Function.identity()));
    Map<String, UserCaseStep> snapshot =
        userCaseStepRepository
            .findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId())
            .stream()
            .collect(Collectors.toMap(UserCaseStep::getStableCode, Function.identity()));

    List<RequirementChange> changes = new ArrayList<>();
    for (String code : allCodes(current.keySet(), snapshot.keySet())) {
      StepVersion currentItem = current.get(code);
      UserCaseStep snapshotItem = snapshot.get(code);
      if (currentItem != null && snapshotItem == null) {
        changes.add(
            new RequirementChange("ADDED", "STEP", code, currentItem.getTitle(), "New step"));
      } else if (currentItem == null) {
        changes.add(
            new RequirementChange(
                "REMOVED", "STEP", code, snapshotItem.getTitleSnapshot(), "Step removed"));
      } else if (stepMaterialChange(currentItem, snapshotItem)) {
        changes.add(
            new RequirementChange(
                "CHANGED", "STEP", code, currentItem.getTitle(), "Step content changed"));
      }
    }
    return changes;
  }

  private boolean stepMaterialChange(StepVersion current, UserCaseStep snapshot) {
    return !Objects.equals(current.getTitle(), snapshot.getTitleSnapshot())
        || !Objects.equals(current.getDescription(), snapshot.getDescriptionSnapshot())
        || !Objects.equals(
            current.getDetailedInstructions(), snapshot.getDetailedInstructionsSnapshot())
        || current.getStepType() != snapshot.getStepType()
        || current.isMandatory() != snapshot.isMandatory();
  }

  private List<RequirementChange> compareDocuments(
      ProcedureVersion currentActive, UserCaseSnapshotRevision revision) {
    Map<String, DocumentRequirementVersion> current =
        documentRequirementVersionRepository
            .findByProcedureVersion_IdOrderBySortOrderAsc(currentActive.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    dv -> dv.getDocumentRequirement().getStableCode(), Function.identity()));
    Map<String, UserCaseDocument> snapshot =
        userCaseDocumentRepository
            .findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId())
            .stream()
            .collect(Collectors.toMap(UserCaseDocument::getStableCode, Function.identity()));

    List<RequirementChange> changes = new ArrayList<>();
    for (String code : allCodes(current.keySet(), snapshot.keySet())) {
      DocumentRequirementVersion currentItem = current.get(code);
      UserCaseDocument snapshotItem = snapshot.get(code);
      if (currentItem != null && snapshotItem == null) {
        changes.add(
            new RequirementChange(
                "ADDED", "DOCUMENT", code, currentItem.getName(), "New document"));
      } else if (currentItem == null) {
        changes.add(
            new RequirementChange(
                "REMOVED", "DOCUMENT", code, snapshotItem.getNameSnapshot(), "Document removed"));
      } else if (documentMaterialChange(currentItem, snapshotItem)) {
        changes.add(
            new RequirementChange(
                "CHANGED",
                "DOCUMENT",
                code,
                currentItem.getName(),
                "Document requirement changed"));
      }
    }
    return changes;
  }

  /**
   * Brief §35/§36: mandatory status, copies, translation/legalisation requirements, and
   * description/instructions are material; a punctuation-only title change is not checked at all
   * (brief §35 - "do not over-engineer semantic text diff," a field-level comparison is enough, and
   * title is deliberately not one of the compared fields).
   */
  private boolean documentMaterialChange(
      DocumentRequirementVersion current, UserCaseDocument snapshot) {
    return current.getRequirementType() != snapshot.getRequirementType()
        || !Objects.equals(current.getNumberOfCopies(), snapshot.getNumberOfCopiesSnapshot())
        || !Objects.equals(current.getOriginalRequired(), snapshot.getOriginalRequiredSnapshot())
        || !Objects.equals(
            current.getTranslationRequired(), snapshot.getTranslationRequiredSnapshot())
        || !Objects.equals(
            current.getSwornTranslationRequired(), snapshot.getSwornTranslationRequiredSnapshot())
        || !Objects.equals(current.getApostilleRequired(), snapshot.getApostilleRequiredSnapshot())
        || !Objects.equals(
            current.getLegalisationRequired(), snapshot.getLegalisationRequiredSnapshot())
        || !Objects.equals(
            current.getValidityPeriodDescription(),
            snapshot.getValidityPeriodDescriptionSnapshot());
  }

  private List<RequirementChange> compareFees(
      ProcedureVersion currentActive, UserCaseSnapshotRevision revision) {
    Map<String, FeeVersion> current =
        feeVersionRepository.findByProcedureVersion_Id(currentActive.getId()).stream()
            .collect(Collectors.toMap(fv -> fv.getFee().getStableCode(), Function.identity()));
    Map<String, UserCaseFee> snapshot =
        userCaseFeeRepository
            .findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId())
            .stream()
            .collect(Collectors.toMap(UserCaseFee::getStableCode, Function.identity()));

    List<RequirementChange> changes = new ArrayList<>();
    for (String code : allCodes(current.keySet(), snapshot.keySet())) {
      FeeVersion currentItem = current.get(code);
      UserCaseFee snapshotItem = snapshot.get(code);
      if (currentItem != null && snapshotItem == null) {
        changes.add(new RequirementChange("ADDED", "FEE", code, code, "New fee"));
      } else if (currentItem == null) {
        changes.add(new RequirementChange("REMOVED", "FEE", code, code, "Fee removed"));
      } else if (currentItem.getAmount().compareTo(snapshotItem.getAmountSnapshot()) != 0
          || !Objects.equals(currentItem.getCurrency(), snapshotItem.getCurrencySnapshot())) {
        changes.add(
            new RequirementChange(
                "CHANGED",
                "FEE",
                code,
                code,
                "%s %s -> %s %s"
                    .formatted(
                        snapshotItem.getAmountSnapshot(), snapshotItem.getCurrencySnapshot(),
                        currentItem.getAmount(), currentItem.getCurrency())));
      }
    }
    return changes;
  }

  private TreeSet<String> allCodes(java.util.Set<String> a, java.util.Set<String> b) {
    TreeSet<String> codes = new TreeSet<>(a);
    codes.addAll(b);
    return codes;
  }
}
