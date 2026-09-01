package com.foreignerwarsaw.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Filter-level 401 (brief §19/§20): fires when an unauthenticated request hits a path that requires
 * authentication - distinct from {@link GlobalExceptionHandler}'s login-specific handlers, which
 * run for exceptions thrown by a manual {@code AuthenticationManager.authenticate(...)} call inside
 * the login controller, not by this filter-chain path.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError body =
        ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "AUTHENTICATION_REQUIRED",
            "Authentication is required");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
