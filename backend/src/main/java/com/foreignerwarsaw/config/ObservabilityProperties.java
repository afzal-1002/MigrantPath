package com.foreignerwarsaw.config;

/**
 * Canonical Phase 14 (Observability) brief §11 - {@code app.observability.*} in application.yml. A
 * request slower than {@code slowRequestThresholdMs} is logged at WARN instead of INFO by {@link
 * RequestLoggingFilter} - never a payload dump, just the same summary fields at a louder level so a
 * slow-request trend is greppable without wading through every routine request.
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "app.observability")
public record ObservabilityProperties(long slowRequestThresholdMs) {}
