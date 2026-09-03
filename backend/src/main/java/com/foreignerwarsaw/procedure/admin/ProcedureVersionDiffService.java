package com.foreignerwarsaw.procedure.admin;

import com.foreignerwarsaw.procedure.admin.dto.ProcedureVersionDiffResponse;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.procedure.core.ProcedureVersionService;
import com.foreignerwarsaw.procedure.document.DocumentRequirementService;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersion;
import com.foreignerwarsaw.procedure.fee.FeeService;
import com.foreignerwarsaw.procedure.fee.FeeVersion;
import com.foreignerwarsaw.procedure.step.ProcedureStepService;
import com.foreignerwarsaw.procedure.step.StepVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A semantic (stable-code-matched) diff between two versions of the same Procedure (brief §68) -
 * reuses exactly the "match by stable code, compare field-by-field" pattern Phase 8's {@code
 * CaseRequirementChangeService} already established for the same reason: a display title can be
 * reworded without the underlying requirement changing, so title is never the diff key.
 */
@Service
public class ProcedureVersionDiffService {

  private final ProcedureVersionService procedureVersionService;
  private final ProcedureStepService procedureStepService;
  private final DocumentRequirementService documentRequirementService;
  private final FeeService feeService;

  public ProcedureVersionDiffService(
      ProcedureVersionService procedureVersionService,
      ProcedureStepService procedureStepService,
      DocumentRequirementService documentRequirementService,
      FeeService feeService) {
    this.procedureVersionService = procedureVersionService;
    this.procedureStepService = procedureStepService;
    this.documentRequirementService = documentRequirementService;
    this.feeService = feeService;
  }

  @Transactional(readOnly = true)
  public ProcedureVersionDiffResponse diff(UUID fromVersionId, UUID toVersionId) {
    ProcedureVersion from = procedureVersionService.getById(fromVersionId);
    ProcedureVersion to = procedureVersionService.getById(toVersionId);

    List<String> overview = new ArrayList<>();
    if (!Objects.equals(from.getTitle(), to.getTitle())) {
      overview.add("title: \"" + from.getTitle() + "\" -> \"" + to.getTitle() + "\"");
    }
    if (!Objects.equals(from.getSummary(), to.getSummary())) {
      overview.add("summary changed");
    }
    if (!Objects.equals(from.getDescription(), to.getDescription())) {
      overview.add("description changed");
    }

    List<StepVersion> fromSteps = procedureStepService.listForVersion(from.getId());
    List<StepVersion> toSteps = procedureStepService.listForVersion(to.getId());
    Map<String, StepVersion> fromStepsByCode =
        toMap(fromSteps, s -> s.getProcedureStep().getStableCode());
    Map<String, StepVersion> toStepsByCode =
        toMap(toSteps, s -> s.getProcedureStep().getStableCode());
    List<String> stepsAdded = added(fromStepsByCode.keySet(), toStepsByCode.keySet());
    List<String> stepsRemoved = added(toStepsByCode.keySet(), fromStepsByCode.keySet());
    List<String> stepsChanged = new ArrayList<>();
    for (String code : fromStepsByCode.keySet()) {
      StepVersion a = fromStepsByCode.get(code);
      StepVersion b = toStepsByCode.get(code);
      if (b == null) {
        continue;
      }
      if (!Objects.equals(a.getTitle(), b.getTitle())
          || !Objects.equals(a.getDescription(), b.getDescription())
          || a.getStepType() != b.getStepType()
          || a.isMandatory() != b.isMandatory()
          || a.getSortOrder() != b.getSortOrder()) {
        stepsChanged.add(code);
      }
    }

    List<DocumentRequirementVersion> fromDocs =
        documentRequirementService.listForVersion(from.getId());
    List<DocumentRequirementVersion> toDocs = documentRequirementService.listForVersion(to.getId());
    Map<String, DocumentRequirementVersion> fromDocsByCode =
        toMap(fromDocs, d -> d.getDocumentRequirement().getStableCode());
    Map<String, DocumentRequirementVersion> toDocsByCode =
        toMap(toDocs, d -> d.getDocumentRequirement().getStableCode());
    List<String> documentsAdded = added(fromDocsByCode.keySet(), toDocsByCode.keySet());
    List<String> documentsRemoved = added(toDocsByCode.keySet(), fromDocsByCode.keySet());
    List<String> documentsChanged = new ArrayList<>();
    for (String code : fromDocsByCode.keySet()) {
      DocumentRequirementVersion a = fromDocsByCode.get(code);
      DocumentRequirementVersion b = toDocsByCode.get(code);
      if (b == null) {
        continue;
      }
      if (a.getRequirementType() != b.getRequirementType()
          || a.isRequiredByDefault() != b.isRequiredByDefault()
          || !Objects.equals(a.getNumberOfCopies(), b.getNumberOfCopies())
          || !Objects.equals(a.getOriginalRequired(), b.getOriginalRequired())
          || !Objects.equals(a.getTranslationRequired(), b.getTranslationRequired())
          || !Objects.equals(a.getSwornTranslationRequired(), b.getSwornTranslationRequired())
          || !Objects.equals(a.getApostilleRequired(), b.getApostilleRequired())
          || !Objects.equals(a.getLegalisationRequired(), b.getLegalisationRequired())) {
        documentsChanged.add(code);
      }
    }

    List<FeeVersion> fromFees = feeService.listForVersion(from.getId());
    List<FeeVersion> toFees = feeService.listForVersion(to.getId());
    Map<String, FeeVersion> fromFeesByCode = toMap(fromFees, f -> f.getFee().getStableCode());
    Map<String, FeeVersion> toFeesByCode = toMap(toFees, f -> f.getFee().getStableCode());
    List<String> feesAdded = added(fromFeesByCode.keySet(), toFeesByCode.keySet());
    List<String> feesRemoved = added(toFeesByCode.keySet(), fromFeesByCode.keySet());
    List<String> feesChanged = new ArrayList<>();
    for (String code : fromFeesByCode.keySet()) {
      FeeVersion a = fromFeesByCode.get(code);
      FeeVersion b = toFeesByCode.get(code);
      if (b == null) {
        continue;
      }
      if (a.getAmount().compareTo(b.getAmount()) != 0
          || !Objects.equals(a.getCurrency(), b.getCurrency())) {
        feesChanged.add(code);
      }
    }

    return new ProcedureVersionDiffResponse(
        from.getId(),
        from.getVersionNumber(),
        to.getId(),
        to.getVersionNumber(),
        overview,
        stepsAdded,
        stepsRemoved,
        stepsChanged,
        documentsAdded,
        documentsRemoved,
        documentsChanged,
        feesAdded,
        feesRemoved,
        feesChanged);
  }

  private static <T> Map<String, T> toMap(List<T> items, Function<T, String> keyFn) {
    Map<String, T> map = new java.util.LinkedHashMap<>();
    for (T item : items) {
      map.put(keyFn.apply(item), item);
    }
    return map;
  }

  /**
   * Codes present in {@code target} but absent from {@code source} - i.e. newly added in target.
   */
  private static List<String> added(java.util.Set<String> source, java.util.Set<String> target) {
    List<String> result = new ArrayList<>();
    for (String code : target) {
      if (!source.contains(code)) {
        result.add(code);
      }
    }
    return result;
  }
}
