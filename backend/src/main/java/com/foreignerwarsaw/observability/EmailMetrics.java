package com.foreignerwarsaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §29 - {@code email.send.success}/{@code
 * email.send.failure}, tagged by the small, fixed {@link Type} enum (never the recipient address -
 * brief §29's own "do not tag recipient").
 */
@Component
public class EmailMetrics {

  public enum Type {
    VERIFICATION,
    PASSWORD_RESET
  }

  private final MeterRegistry meterRegistry;

  public EmailMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordSuccess(Type type) {
    Counter.builder("email.send.success")
        .description("Emails successfully handed to the SMTP transport")
        .tag("type", type.name())
        .register(meterRegistry)
        .increment();
  }

  public void recordFailure(Type type) {
    Counter.builder("email.send.failure")
        .description("Emails that failed to send")
        .tag("type", type.name())
        .register(meterRegistry)
        .increment();
  }
}
