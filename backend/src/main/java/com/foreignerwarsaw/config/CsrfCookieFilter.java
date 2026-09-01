package com.foreignerwarsaw.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 6+ made CSRF token resolution lazy/deferred by default (a BREACH- attack
 * mitigation) - the {@code XSRF-TOKEN} cookie is only actually written once something reads the
 * token. A server-rendered app naturally triggers that by rendering the token into a page; a pure
 * JSON API serving an Angular SPA never does, so without this filter the cookie would never appear
 * and Angular's built-in XSRF interceptor would have nothing to read on the very first request.
 * This is Spring Security's own documented "Integrating CSRF Protection With… a JavaScript
 * frontend" recipe, not a custom workaround - see ADR-005.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      // Force the deferred token to resolve now, which is what actually triggers the
      // CookieCsrfTokenRepository to write the XSRF-TOKEN cookie on this response.
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
