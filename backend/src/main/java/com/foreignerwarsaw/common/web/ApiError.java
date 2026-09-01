package com.foreignerwarsaw.common.web;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response envelope for the whole API, per docs/architecture/ARCHITECTURE.md §6.
 * Every controller-facing error - now and in every future phase - is rendered through this shape,
 * never a raw stack trace or a framework-default error body.
 */
public record ApiError(
    Instant timestamp, int status, String code, String message, List<FieldViolation> errors) {

  public ApiError {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static ApiError of(int status, String code, String message) {
    return new ApiError(Instant.now(), status, code, message, List.of());
  }

  public static ApiError of(int status, String code, String message, List<FieldViolation> errors) {
    return new ApiError(Instant.now(), status, code, message, errors);
  }

  /** One field-level validation failure, as shown in the ARCHITECTURE.md §6 example. */
  public record FieldViolation(String field, String message) {}
}
