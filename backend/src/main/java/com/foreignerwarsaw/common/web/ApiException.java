package com.foreignerwarsaw.common.web;

import org.springframework.http.HttpStatus;

/**
 * Thrown by service code for any business-rule/security failure that should reach the client as a
 * specific {@link ApiError} code (brief §20) rather than a generic 500 - caught centrally by {@link
 * GlobalExceptionHandler}. Controllers never build {@link ApiError} responses by hand for these
 * cases; they just let this propagate.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
