package com.foreignerwarsaw.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

/**
 * Phase 11 brief §40/§42 - the first of the named counters, wired with zero changes to any
 * already-tested authentication code path: Spring Security already publishes an {@link
 * AbstractAuthenticationFailureEvent} (and its subclasses - bad credentials, locked, disabled, …)
 * on every failed authentication attempt, regardless of caller; this only listens, it never
 * participates in the authentication decision itself.
 *
 * <p>No PII in the metric's labels/tags (brief §42 - "never include country/salary/legal status/
 * email/user ID") - {@link Counter} here carries no tags at all, just a single running count.
 * {@link MeterRegistry} is already on the classpath transitively via {@code
 * spring-boot-starter-actuator} (Micrometer's core), so this adds no new dependency.
 */
@Component
public class SecurityMetricsListener {

  private final Counter loginFailureCounter;

  public SecurityMetricsListener(MeterRegistry meterRegistry) {
    this.loginFailureCounter =
        Counter.builder("auth.login.failure")
            .description("Failed authentication attempts (bad credentials, locked, or disabled)")
            .register(meterRegistry);
  }

  @EventListener
  public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
    loginFailureCounter.increment();
  }
}
