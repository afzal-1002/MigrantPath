package com.foreignerwarsaw.procedure.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureRepository extends JpaRepository<Procedure, UUID> {

  /**
   * {@code JOIN FETCH p.category} - both {@link
   * com.foreignerwarsaw.procedure.core.dto.ProcedureSummaryResponse} and {@link
   * com.foreignerwarsaw.procedure.core.dto.ProcedureDetailResponse} read {@code
   * category.getCode()}.
   */
  @Query("SELECT p FROM Procedure p JOIN FETCH p.category WHERE upper(p.code) = upper(:code)")
  Optional<Procedure> findByCodeIgnoreCase(@Param("code") String code);

  boolean existsByCodeIgnoreCase(String code);

  /**
   * {@code JOIN FETCH p.category} - {@link Procedure#getCategory()} is read by {@link
   * com.foreignerwarsaw.procedure.core.dto.ProcedureSummaryResponse#from} after this transactional
   * service method returns; the plain inherited {@code findAll} leaves {@code category} an
   * uninitialized lazy proxy (the same class of bug fixed elsewhere in this phase - see {@code
   * ProcedureVersionRepository#findByIdFetchingProcedure}'s Javadoc).
   */
  @Query("SELECT p FROM Procedure p JOIN FETCH p.category")
  List<Procedure> findAllFetchingCategory();
}
