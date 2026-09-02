package com.foreignerwarsaw.procedure.threshold;

import com.foreignerwarsaw.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class ThresholdNotFoundException extends ApiException {

  public ThresholdNotFoundException(String code) {
    super(
        HttpStatus.NOT_FOUND, "THRESHOLD_NOT_FOUND", "No threshold found for code '" + code + "'");
  }
}
