package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.category.ProcedureCategory;
import com.foreignerwarsaw.procedure.category.ProcedureCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procedure identity creation/lookup only - "what is this procedure" (brief's own core
 * distinction), never eligibility. See {@link ProcedureVersionService} for versioned content and
 * {@link ProcedureQueryService} for the public read path.
 */
@Service
public class ProcedureService {

  private final ProcedureRepository procedureRepository;
  private final ProcedureCategoryRepository procedureCategoryRepository;

  public ProcedureService(
      ProcedureRepository procedureRepository,
      ProcedureCategoryRepository procedureCategoryRepository) {
    this.procedureRepository = procedureRepository;
    this.procedureCategoryRepository = procedureCategoryRepository;
  }

  @Transactional
  public Procedure createProcedure(
      String code,
      String categoryCode,
      String canonicalName,
      String shortDescription,
      JurisdictionScope scope) {
    if (procedureRepository.existsByCodeIgnoreCase(code)) {
      throw new ApiException(
          HttpStatus.CONFLICT, "PROCEDURE_CODE_TAKEN", "Procedure code already exists: " + code);
    }
    ProcedureCategory category =
        procedureCategoryRepository
            .findByCodeIgnoreCase(categoryCode)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "UNKNOWN_CATEGORY",
                        "Unknown category code: " + categoryCode));
    Procedure procedure = Procedure.create(code, category, canonicalName, shortDescription, scope);
    return procedureRepository.save(procedure);
  }

  @Transactional(readOnly = true)
  public Procedure getByCode(String code) {
    return procedureRepository
        .findByCodeIgnoreCase(code)
        .orElseThrow(() -> new ProcedureNotFoundException(code));
  }

  @Transactional(readOnly = true)
  public Optional<Procedure> findByCode(String code) {
    return procedureRepository.findByCodeIgnoreCase(code);
  }

  @Transactional(readOnly = true)
  public List<Procedure> listActive() {
    return procedureRepository.findAll().stream().filter(Procedure::isActive).toList();
  }
}
