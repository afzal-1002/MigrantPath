package com.foreignerwarsaw.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) token retention (brief §34/§35/§40) - see {@code
 * app.token-cleanup.*} in application.yml for the actual values and reasoning.
 */
@ConfigurationProperties(prefix = "app.token-cleanup")
public record TokenCleanupProperties(
    boolean enabled, Duration usedTokenRetention, Duration interval) {}
