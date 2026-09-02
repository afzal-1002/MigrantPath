package com.foreignerwarsaw.common.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Thrown by service code for any business-rule/security failure that should reach the client as a
 * specific {@link ApiError} code (brief §20) rather than a generic 500 - caught centrally by {@link
 * GlobalExceptionHandler}. Controllers never build {@link ApiError} responses by hand for these
 * cases; they just let this propagate.
 *
 * <p>{@code errors} (Phase 5 addition, brief §61) optionally carries field-level violations for a
 * single request that fails validation on more than one answer field at once (e.g. {@code
 * INVALID_ASSESSMENT_ANSWER}) - empty for every pre-Phase-5 call site, which keeps using the 3-arg
 * constructor unchanged.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<ApiError.FieldViolation> errors;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of());
  }

  public ApiException(
      HttpStatus status, String code, String message, List<ApiError.FieldViolation> errors) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public List<ApiError.FieldViolation> getErrors() {
    return errors;
  }
}
