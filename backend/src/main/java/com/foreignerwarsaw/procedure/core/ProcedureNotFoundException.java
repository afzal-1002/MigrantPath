package com.foreignerwarsaw.procedure.core;

import com.foreignerwarsaw.common.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Also thrown by the public read API when a procedure exists but has no currently active PUBLISHED
 * version - deliberately the same code/status as "code doesn't exist at all" (brief §38): procedure
 * codes aren't secret, so there's nothing to protect by distinguishing the two, and one honest
 * "nothing publicly visible here right now" is simpler than an enumeration-style pair of codes.
 */
public class ProcedureNotFoundException extends ApiException {

  public ProcedureNotFoundException(String code) {
    super(
        HttpStatus.NOT_FOUND, "PROCEDURE_NOT_FOUND", "No procedure found for code '" + code + "'");
  }
}
