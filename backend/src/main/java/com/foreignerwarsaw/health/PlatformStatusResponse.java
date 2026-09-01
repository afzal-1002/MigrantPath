package com.foreignerwarsaw.health;

/**
 * Deliberately minimal, non-sensitive payload (brief §14: "must contain no sensitive information")
 * - just enough for the frontend to prove connectivity and show the running backend version.
 */
public record PlatformStatusResponse(String status, String application, String version) {}
