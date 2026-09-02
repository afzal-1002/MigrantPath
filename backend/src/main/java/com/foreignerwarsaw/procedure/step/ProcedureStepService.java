package com.foreignerwarsaw.procedure.step;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding/listing steps on a DRAFT {@link ProcedureVersion} (brief §55's "cohesive services," not
 * one giant ProcedureService). Publish-time completeness validation lives in {@code
 * ProcedurePublishingService}, not here - this class only ever writes DRAFT content.
 */
@Service
public class ProcedureStepService {

  private final ProcedureStepRepository procedureStepRepository;
  private final StepVersionRepository stepVersionRepository;

  public ProcedureStepService(
      ProcedureStepRepository procedureStepRepository,
      StepVersionRepository stepVersionRepository) {
    this.procedureStepRepository = procedureStepRepository;
    this.stepVersionRepository = stepVersionRepository;
  }

  @Transactional
  public StepVersion addStep(
      ProcedureVersion procedureVersion,
      String stableCode,
      String title,
      String description,
      StepType stepType,
      int sortOrder,
      boolean mandatory) {
    requireDraft(procedureVersion);
    Procedure procedure = procedureVersion.getProcedure();
    ProcedureStep step =
        procedureStepRepository
            .findByProcedure_IdAndStableCode(procedure.getId(), stableCode)
            .orElseGet(
                () -> procedureStepRepository.save(new ProcedureStep(procedure, stableCode)));
    StepVersion stepVersion =
        new StepVersion(step, procedureVersion, title, description, stepType, sortOrder, mandatory);
    return stepVersionRepository.save(stepVersion);
  }

  @Transactional(readOnly = true)
  public List<StepVersion> listForVersion(UUID procedureVersionId) {
    return stepVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(procedureVersionId);
  }

  private void requireDraft(ProcedureVersion procedureVersion) {
    if (procedureVersion.getStatus() != PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT, "VERSION_NOT_DRAFT", "Steps can only be added to a DRAFT version");
    }
  }
}
