package com.foreignerwarsaw.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralizes every authentication-related tunable (brief §35: "do not scatter expiration numbers
 * through services") - see {@code app.auth.*} in application.yml for the actual values and the
 * reasoning behind each one.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    String frontendBaseUrl,
    Duration emailVerificationTokenTtl,
    Duration passwordResetTokenTtl,
    int maxFailedLoginAttempts,
    Duration lockoutDuration,
    Duration resendVerificationCooldown,
    Duration forgotPasswordCooldown) {}
