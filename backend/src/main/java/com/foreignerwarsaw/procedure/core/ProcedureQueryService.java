package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.procedure.authority.ProcedureAuthorityRepository;
import com.foreignerwarsaw.procedure.authority.ProcedureVersionOfficeRepository;
import com.foreignerwarsaw.procedure.core.dto.DocumentRequirementResponse;
import com.foreignerwarsaw.procedure.core.dto.FeeResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureAuthorityRefResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureDetailResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureOfficeRefResponse;
import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import com.foreignerwarsaw.procedure.core.dto.StepResponse;
import com.foreignerwarsaw.procedure.document.DocumentRequirementVersionRepository;
import com.foreignerwarsaw.procedure.fee.FeeVersionRepository;
import com.foreignerwarsaw.procedure.step.StepVersionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public read path - resolves the {@link ProcedureVersion} active as of an evaluation date
 * (default: today) via {@link ProcedureVersionRepository}'s one authoritative Active-Version
 * Predicate query, then assembles the detail DTO from several bounded, to-one-fetch-joined queries
 * (brief §99/§100) rather than one large multi-collection {@code JOIN FETCH} that would produce a
 * Cartesian product across steps × documents × fees × sources.
 */
@Service
public class ProcedureQueryService {

  private final ProcedureRepository procedureRepository;
  private final ProcedureVersionRepository procedureVersionRepository;
  private final StepVersionRepository stepVersionRepository;
  private final DocumentRequirementVersionRepository documentRequirementVersionRepository;
  private final FeeVersionRepository feeVersionRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final ProcedureAuthorityRepository procedureAuthorityRepository;
  private final ProcedureVersionOfficeRepository procedureVersionOfficeRepository;
  private final Clock clock;

  public ProcedureQueryService(
      ProcedureRepository procedureRepository,
      ProcedureVersionRepository procedureVersionRepository,
      StepVersionRepository stepVersionRepository,
      DocumentRequirementVersionRepository documentRequirementVersionRepository,
      FeeVersionRepository feeVersionRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      ProcedureAuthorityRepository procedureAuthorityRepository,
      ProcedureVersionOfficeRepository procedureVersionOfficeRepository,
      Clock clock) {
    this.procedureRepository = procedureRepository;
    this.procedureVersionRepository = procedureVersionRepository;
    this.stepVersionRepository = stepVersionRepository;
    this.documentRequirementVersionRepository = documentRequirementVersionRepository;
    this.feeVersionRepository = feeVersionRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.procedureAuthorityRepository = procedureAuthorityRepository;
    this.procedureVersionOfficeRepository = procedureVersionOfficeRepository;
    this.clock = clock;
  }

  /**
   * Only procedures with a currently active PUBLISHED version (brief §36/§65's "graceful empty
   * state" is what a caller sees when nothing is published yet - never a broken link to a procedure
   * with no visible content).
   */
  @Transactional(readOnly = true)
  public List<Procedure> listPublished() {
    LocalDate today = LocalDate.now(clock);
    return procedureRepository.findAllFetchingCategory().stream()
        .filter(Procedure::isActive)
        .filter(
            p ->
                procedureVersionRepository.findActivePublishedVersion(p.getId(), today).isPresent())
        .toList();
  }

  /**
   * Same 404 for "code doesn't exist" and "exists but nothing is publicly visible right now" - see
   * {@link ProcedureNotFoundException}'s Javadoc.
   */
  @Transactional(readOnly = true)
  public ProcedureDetailResponse getPublishedDetail(String code) {
    return getPublishedDetail(code, LocalDate.now(clock));
  }

  /**
   * Internal/test-only evaluation-date overload (brief §39) - never exposed on the public endpoint,
   * only used directly by tests proving temporal resolution.
   */
  @Transactional(readOnly = true)
  public ProcedureDetailResponse getPublishedDetail(String code, LocalDate evaluationDate) {
    Procedure procedure =
        procedureRepository
            .findByCodeIgnoreCase(code)
            .orElseThrow(() -> new ProcedureNotFoundException(code));
    ProcedureVersion version =
        procedureVersionRepository
            .findActivePublishedVersion(procedure.getId(), evaluationDate)
            .orElseThrow(() -> new ProcedureNotFoundException(code));

    List<StepResponse> steps =
        stepVersionRepository.findByProcedureVersion_IdOrderBySortOrderAsc(version.getId()).stream()
            .map(StepResponse::from)
            .toList();
    List<DocumentRequirementResponse> documents =
        documentRequirementVersionRepository
            .findByProcedureVersion_IdOrderBySortOrderAsc(version.getId())
            .stream()
            .map(DocumentRequirementResponse::from)
            .toList();
    List<FeeResponse> fees =
        feeVersionRepository.findByProcedureVersion_Id(version.getId()).stream()
            .map(FeeResponse::from)
            .toList();
    List<SourceResponse> sources =
        procedureVersionSourceRepository.findByProcedureVersion_Id(version.getId()).stream()
            .map(s -> SourceResponse.from(s.getOfficialSource(), s.getRole().name()))
            .toList();
    List<ProcedureAuthorityRefResponse> authorities =
        procedureAuthorityRepository.findByProcedure_Id(procedure.getId()).stream()
            .map(ProcedureAuthorityRefResponse::from)
            .toList();
    List<ProcedureOfficeRefResponse> offices =
        procedureVersionOfficeRepository.findByProcedureVersion_Id(version.getId()).stream()
            .map(pvo -> ProcedureOfficeRefResponse.from(pvo.getOffice()))
            .toList();

    return ProcedureDetailResponse.of(
        procedure, version, steps, documents, fees, authorities, offices, sources);
  }
}
