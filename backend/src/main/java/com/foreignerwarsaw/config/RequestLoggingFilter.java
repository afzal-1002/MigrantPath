package com.foreignerwarsaw.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Canonical Phase 14 (Observability) brief §9/§10/§11 - one summary log line per completed request:
 * method, a low-cardinality path *template* (never a raw UUID-bearing path - brief §10), status,
 * and duration. Never the query string (brief §9's own "avoid logging raw query strings if they may
 * contain tokens" - the verification/reset-token routes are exactly why, see
 * docs/operations/OBSERVABILITY.md) and never a request/response body.
 *
 * <p>Runs just after {@link CorrelationIdFilter} (same {@code HIGHEST_PRECEDENCE}-adjacent
 * ordering, one step later so the correlation id already exists in MDC and the completed response
 * status is known before this logs) so every summary line already carries {@code correlationId} via
 * the JSON encoder's MDC inclusion (see {@code logback-spring.xml}) - also attached as an explicit
 * structured argument here so it is visible in the local plain-text console pattern too, not only
 * in staging/production's JSON.
 *
 * <p>The path template comes from {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}, set by
 * {@code DispatcherServlet} during handler resolution and readable only *after* {@code
 * filterChain.doFilter} returns - falls back to the raw path (still query-string-free) for a route
 * Spring never matched (a genuine 404, or a static/proxied asset the dispatcher doesn't own).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private final ObservabilityProperties properties;

  public RequestLoggingFilter(ObservabilityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = System.currentTimeMillis() - start;
      Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String pathTemplate =
          matchedPattern != null ? matchedPattern.toString() : request.getRequestURI();
      boolean slow = durationMs >= properties.slowRequestThresholdMs();

      Object[] args = {
        StructuredArguments.kv("requestMethod", request.getMethod()),
        StructuredArguments.kv("requestPath", pathTemplate),
        StructuredArguments.kv("responseStatus", response.getStatus()),
        StructuredArguments.kv("durationMs", durationMs),
      };
      if (slow) {
        log.warn("Slow request: {} {} -> {} ({} ms)", args);
      } else {
        log.info("{} {} -> {} ({} ms)", args);
      }
    }
  }
}
