package com.foreignerwarsaw.common.web;

import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;

/**
 * Standard error response envelope for the whole API, per docs/architecture/ARCHITECTURE.md §6.
 * Every controller-facing error - now and in every future phase - is rendered through this shape,
 * never a raw stack trace or a framework-default error body.
 *
 * <p>Canonical Phase 14 (Observability) brief §8: {@code correlationId} is always populated from
 * the same SLF4J MDC key ({@code correlationId}) {@code CorrelationIdFilter} already sets for every
 * request - so a user reporting "something went wrong" can quote this back, and support can grep
 * the structured logs for the exact same value (already echoed as the {@code X-Correlation-ID}
 * response header, this is the same id surfaced a second, more discoverable way inside the body
 * every API error already has). Never a stack trace, never internal detail - just the id.
 */
public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String correlationId,
    List<FieldViolation> errors) {

  public ApiError {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static ApiError of(int status, String code, String message) {
    return new ApiError(Instant.now(), status, code, message, MDC.get("correlationId"), List.of());
  }

  public static ApiError of(int status, String code, String message, List<FieldViolation> errors) {
    return new ApiError(Instant.now(), status, code, message, MDC.get("correlationId"), errors);
  }

  /** One field-level validation failure, as shown in the ARCHITECTURE.md §6 example. */
  public record FieldViolation(String field, String message) {}
}
