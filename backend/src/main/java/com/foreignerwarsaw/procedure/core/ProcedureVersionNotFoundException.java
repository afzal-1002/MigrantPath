package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class ProcedureVersionNotFoundException extends ApiException {

  public ProcedureVersionNotFoundException(String procedureCode, int versionNumber) {
    super(
        HttpStatus.NOT_FOUND,
        "PROCEDURE_VERSION_NOT_FOUND",
        "No version %d found for procedure '%s'".formatted(versionNumber, procedureCode));
  }
}
