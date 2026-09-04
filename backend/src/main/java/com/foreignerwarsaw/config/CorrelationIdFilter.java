package com.foreignerwarsaw.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlation ID (Phase 11 brief §44): every request gets one, threaded through every log line for
 * its duration via SLF4J's {@link MDC} (see {@code logback-spring.xml}'s {@code %X{correlationId}}
 * pattern), and echoed back as {@code X-Correlation-ID} so a client/caller can quote it back when
 * reporting an issue - the same id a support/ops response can then grep the logs for.
 *
 * <p>An incoming {@code X-Correlation-ID} is honored (useful when a caller already has one - e.g.
 * chained from a reverse proxy's own request-id, or a synthetic monitor script), but only after
 * validation: brief §44 explicitly warns against trusting an arbitrary, unbounded value from the
 * network - a new one is generated instead of a supplied value that's absent, too long, or contains
 * anything other than the safe id characters a UUID/short token would actually use.
 *
 * <p>{@code @Order} runs this before Spring Security's own filter chain (registered via {@code
 * DelegatingFilterProxy} at a fixed, later position) so authentication-failure/exception- handling
 * log lines from deeper in the chain still carry the same id, not just successful requests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Correlation-ID";
  private static final String MDC_KEY = "correlationId";
  private static final int MAX_LENGTH = 100;
  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,100}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String correlationId =
        (incoming != null && incoming.length() <= MAX_LENGTH && SAFE_ID.matcher(incoming).matches())
            ? incoming
            : UUID.randomUUID().toString();

    MDC.put(MDC_KEY, correlationId);
    response.setHeader(HEADER, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      // Always cleared, success or exception - MDC is thread-local and this thread
      // returns to a pool afterward, so a stale value here would leak into the next,
      // unrelated request handled by the same thread.
      MDC.remove(MDC_KEY);
    }
  }
}
