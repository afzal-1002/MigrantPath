package com.foreignerwarsaw.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foreignerwarsaw.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Filter-level 403: an authenticated request whose principal lacks the required authority (brief
 * §19/§20). Not reachable by an unauthenticated request - that's {@link
 * RestAuthenticationEntryPoint}'s 401 instead.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError body =
        ApiError.of(
            HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", "You do not have permission to do that");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
