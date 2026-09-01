package com.foreignerwarsaw.common.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Minimal Phase 1 error-handling foundation: establishes the {@link ApiError} response shape end to
 * end and guarantees no stack trace ever reaches a client, without yet modeling business exceptions
 * (auth, not-found, conflict, ...) that later phases will add - see
 * docs/architecture/ARCHITECTURE.md §6 and IMPLEMENTATION_PLAN.md Phase 1 §29.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError.FieldViolation> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fieldError ->
                    new ApiError.FieldViolation(
                        fieldError.getField(), fieldError.getDefaultMessage()))
            .toList();
    ApiError body =
        ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Request validation failed",
            violations);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    // Logged with full detail server-side (correlation ID once request logging
    // lands, see IMPLEMENTATION_PLAN.md 1.6); the client only ever sees a generic
    // message - never ex.getMessage() or a stack trace (brief §97 / ARCHITECTURE.md
    // §11).
    log.error("Unhandled exception", ex);
    ApiError body =
        ApiError.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
