package com.foreignerwarsaw.procedure.source;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class OfficialSourceNotFoundException extends ApiException {

  public OfficialSourceNotFoundException(UUID id) {
    super(
        HttpStatus.NOT_FOUND,
        "OFFICIAL_SOURCE_NOT_FOUND",
        "No official source found for id '" + id + "'");
  }
}
