package com.foreignerwarsaw.common.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every controller-facing error - now and in every future phase - is rendered through {@link
 * ApiError}, never a raw stack trace or a framework-default error body (brief §20/§97,
 * docs/architecture/ARCHITECTURE.md §6).
 *
 * <p>Note on 401 vs 403 (brief §19): the handlers here for {@link BadCredentialsException}/ {@link
 * LockedException}/{@link DisabledException} only ever fire for exceptions thrown by {@code
 * AuthenticationManager.authenticate(...)} called directly from {@link
 * com.foreignerwarsaw.auth.LoginService} (a plain application call, not a security-filter
 * interception) - login failing is "you are not authenticated," hence 401 for all three, not 403. A
 * *separate* code path (SecurityConfig's {@code AuthenticationEntryPoint}/{@code
 * AccessDeniedHandler} beans) handles the filter-level cases: no session at all on a protected
 * endpoint (401 AUTHENTICATION_REQUIRED) vs authenticated-but-wrong-role (403 ACCESS_DENIED) - see
 * SecurityConfig for that half.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApiException(ApiException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiError.of(ex.getStatus().value(), ex.getCode(), ex.getMessage(), ex.getErrors()));
  }

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

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
    // Canonical Phase 13 (Deployment) real finding: malformed JSON, or a required
    // *primitive* field (boolean/int/etc - a wrapper type instead would just bind to
    // null and let @NotNull/bean validation handle it normally) missing from the
    // request body - e.g. RegisterRequest.acceptTerms omitted entirely - previously
    // fell through to the generic Exception handler below as a 500, discovered by
    // actually curling a real registration request without every field through the
    // deployed reverse proxy, not assumed from reading the code. This is a client
    // input error (400), not a server fault - never ex.getMessage() in the body
    // (brief §97 still applies: no internal deserialization detail like field/class
    // names leaks to the client), full detail stays server-side only.
    log.warn("Unreadable/malformed request body", ex);
    ApiError body =
        ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            "MALFORMED_REQUEST_BODY",
            "The request body is missing a required field or is not valid JSON");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  // --- Login-path authentication outcomes (see class Javadoc) ---

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCredentials() {
    ApiError body =
        ApiError.of(
            HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS", "Invalid email or password");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(LockedException.class)
  public ResponseEntity<ApiError> handleLocked() {
    ApiError body =
        ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "ACCOUNT_LOCKED",
            "This account is temporarily locked due to repeated failed sign-in attempts");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<ApiError> handleDisabled() {
    ApiError body =
        ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "EMAIL_NOT_VERIFIED",
            "Please verify your email address before signing in");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied() {
    // Reached only if an @PreAuthorize-style check fails *inside* a controller method
    // already past the filter chain - SecurityConfig's AccessDeniedHandler covers the
    // filter-level case (brief §17's authorization foundation).
    ApiError body =
        ApiError.of(
            HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", "You do not have permission to do that");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNotFound() {
    // Only reachable for an authenticated request (or a request to a permitted public
    // path) to a route that genuinely doesn't exist - an unauthenticated request to a
    // protected-but-nonexistent path gets 401 first, by design (brief §19's own
    // "avoid blanket 403" guidance, applied without creating a 404-vs-401 enumeration
    // oracle - see docs/architecture ADR-005/SecurityConfig for the full rationale).
    ApiError body = ApiError.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Resource not found");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    // Logged with full detail server-side; the client only ever sees a generic
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
