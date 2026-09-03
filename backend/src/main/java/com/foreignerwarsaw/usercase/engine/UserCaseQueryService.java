package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.procedure.authority.ProcedureAuthorityRepository;
import com.foreignerwarsaw.procedure.authority.ProcedureVersionOfficeRepository;
import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.core.dto.ProcedureAuthorityRefResponse;
import com.foreignerwarsaw.procedure.core.dto.ProcedureOfficeRefResponse;
import com.foreignerwarsaw.procedure.core.dto.SourceResponse;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseFee;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import com.foreignerwarsaw.usercase.core.UserCaseSnapshotRevision;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import com.foreignerwarsaw.usercase.engine.dto.CaseDetailResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseDocumentResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseEventResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseFeeResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseProgressResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseStepResponse;
import com.foreignerwarsaw.usercase.engine.dto.CaseSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side DTO assembly (brief §57/§58) - bounded, per-collection queries rather than one giant
 * multi-collection {@code JOIN FETCH} (brief §144, mirroring {@code ProcedureQueryService}'s own
 * documented reasoning for the identical Cartesian-product risk). Authorities/offices/sources are
 * resolved fresh, at read time, from the case's pinned {@code procedure_id}/{@code
 * procedure_version_id} (brief §17/§47's "recommended: snapshot identity/role, current contact info
 * from current reference data") - never a separate {@code UserCaseAuthority}/{@code UserCaseOffice}
 * table (see docs/cases/CASE_SNAPSHOT_POLICY.md).
 */
@Service
public class UserCaseQueryService {

  private final UserCaseAccessService accessService;
  private final UserCaseRepository userCaseRepository;
  private final UserCaseStepRepository stepRepository;
  private final UserCaseDocumentRepository documentRepository;
  private final UserCaseFeeRepository feeRepository;
  private final UserCaseEventRepository eventRepository;
  private final UserCaseProgressService progressService;
  private final CaseRequirementChangeService changeService;
  private final ProcedureAuthorityRepository procedureAuthorityRepository;
  private final ProcedureVersionOfficeRepository procedureVersionOfficeRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;

  public UserCaseQueryService(
      UserCaseAccessService accessService,
      UserCaseRepository userCaseRepository,
      UserCaseStepRepository stepRepository,
      UserCaseDocumentRepository documentRepository,
      UserCaseFeeRepository feeRepository,
      UserCaseEventRepository eventRepository,
      UserCaseProgressService progressService,
      CaseRequirementChangeService changeService,
      ProcedureAuthorityRepository procedureAuthorityRepository,
      ProcedureVersionOfficeRepository procedureVersionOfficeRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository) {
    this.accessService = accessService;
    this.userCaseRepository = userCaseRepository;
    this.stepRepository = stepRepository;
    this.documentRepository = documentRepository;
    this.feeRepository = feeRepository;
    this.eventRepository = eventRepository;
    this.progressService = progressService;
    this.changeService = changeService;
    this.procedureAuthorityRepository = procedureAuthorityRepository;
    this.procedureVersionOfficeRepository = procedureVersionOfficeRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
  }

  @Transactional(readOnly = true)
  public List<CaseSummaryResponse> listForUser(UUID userId) {
    return userCaseRepository.findByUser_IdOrderByUpdatedAtDesc(userId).stream()
        .map(this::toSummary)
        .toList();
  }

  private CaseSummaryResponse toSummary(UserCase userCase) {
    UserCaseSnapshotRevision revision = userCase.getCurrentRevision();
    List<UserCaseStep> steps =
        revision == null
            ? List.of()
            : stepRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId());
    List<UserCaseDocument> documents =
        revision == null
            ? List.of()
            : documentRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId());
    CaseProgress progress = progressService.calculate(steps, documents);
    boolean hasUpdates = changeService.detectChanges(userCase).newerVersionAvailable();

    return new CaseSummaryResponse(
        userCase.getId(),
        userCase.getProcedure().getCode(),
        revision != null
            ? revision.getProcedureVersion().getTitle()
            : userCase.getProcedure().getCanonicalName(),
        userCase.getStatus().name(),
        progress.stepsCompleted(),
        progress.stepsTotal(),
        progress.documentsReady(),
        progress.documentsTotal(),
        hasUpdates,
        userCase.getUpdatedAt());
  }

  @Transactional(readOnly = true)
  public CaseDetailResponse getDetail(UUID caseId, UUID userId) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    return toDetail(userCase);
  }

  private CaseDetailResponse toDetail(UserCase userCase) {
    UserCaseSnapshotRevision revision = userCase.getCurrentRevision();
    List<UserCaseStep> steps =
        revision == null
            ? List.of()
            : stepRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId());
    List<UserCaseDocument> documents =
        revision == null
            ? List.of()
            : documentRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId());
    List<CaseFeeResponse> fees =
        (revision == null
                ? List.<UserCaseFee>of()
                : feeRepository.findBySnapshotRevision_IdOrderBySortOrderAsc(revision.getId()))
            .stream().map(CaseFeeResponse::from).toList();

    CaseProgress progress = progressService.calculate(steps, documents);
    boolean hasUpdates = changeService.detectChanges(userCase).newerVersionAvailable();

    List<ProcedureAuthorityRefResponse> authorities =
        procedureAuthorityRepository.findByProcedure_Id(userCase.getProcedure().getId()).stream()
            .map(ProcedureAuthorityRefResponse::from)
            .toList();
    List<ProcedureOfficeRefResponse> offices =
        revision == null
            ? List.of()
            : procedureVersionOfficeRepository
                .findByProcedureVersion_Id(revision.getProcedureVersion().getId())
                .stream()
                .map(pvo -> ProcedureOfficeRefResponse.from(pvo.getOffice()))
                .toList();
    List<SourceResponse> sources =
        revision == null
            ? List.of()
            : procedureVersionSourceRepository
                .findByProcedureVersion_Id(revision.getProcedureVersion().getId())
                .stream()
                .map(s -> SourceResponse.from(s.getOfficialSource(), s.getRole().name()))
                .toList();

    return new CaseDetailResponse(
        userCase.getId(),
        userCase.getProcedure().getCode(),
        revision != null
            ? revision.getProcedureVersion().getTitle()
            : userCase.getProcedure().getCanonicalName(),
        userCase.getStatus().name(),
        userCase.getCreatedAt(),
        userCase.getUpdatedAt(),
        userCase.getSubmittedAt(),
        userCase.getCompletedAt(),
        revision != null ? revision.getEvaluationDate() : null,
        revision != null ? revision.getRevisionNumber() : 0,
        CaseProgressResponse.from(progress),
        steps.stream().map(CaseStepResponse::from).toList(),
        documents.stream().map(CaseDocumentResponse::from).toList(),
        fees,
        authorities,
        offices,
        sources,
        hasUpdates);
  }

  @Transactional(readOnly = true)
  public List<CaseEventResponse> getEvents(UUID caseId, UUID userId) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    return eventRepository.findByUserCase_IdOrderByOccurredAtDesc(userCase.getId()).stream()
        .map(CaseEventResponse::from)
        .toList();
  }
}
