package com.foreignerwarsaw.procedure.authority;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.reference.authority.Authority;
import com.foreignerwarsaw.reference.authority.Office;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcedureAuthorityService {

  private final ProcedureAuthorityRepository procedureAuthorityRepository;
  private final ProcedureVersionOfficeRepository procedureVersionOfficeRepository;

  public ProcedureAuthorityService(
      ProcedureAuthorityRepository procedureAuthorityRepository,
      ProcedureVersionOfficeRepository procedureVersionOfficeRepository) {
    this.procedureAuthorityRepository = procedureAuthorityRepository;
    this.procedureVersionOfficeRepository = procedureVersionOfficeRepository;
  }

  @Transactional
  public void attachAuthority(
      Procedure procedure, Authority authority, ProcedureAuthorityRole role) {
    procedureAuthorityRepository.save(new ProcedureAuthority(procedure, authority, role));
  }

  @Transactional
  public void attachOffice(ProcedureVersion procedureVersion, Office office) {
    procedureVersionOfficeRepository.save(new ProcedureVersionOffice(procedureVersion, office));
  }

  @Transactional(readOnly = true)
  public List<ProcedureAuthority> authoritiesFor(UUID procedureId) {
    return procedureAuthorityRepository.findByProcedure_Id(procedureId);
  }

  @Transactional(readOnly = true)
  public List<ProcedureVersionOffice> officesFor(UUID procedureVersionId) {
    return procedureVersionOfficeRepository.findByProcedureVersion_Id(procedureVersionId);
  }
}
