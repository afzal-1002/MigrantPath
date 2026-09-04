package com.foreignerwarsaw.health;

/**
 * Deliberately minimal, non-sensitive payload (brief §14/§78: "must contain no sensitive
 * information" - no host, no filesystem path, no Java args, no config values) - just enough for the
 * frontend to prove connectivity and for an operator to confirm which build is actually running.
 * {@code commit} is a short git SHA (or {@code "unknown"} for a `spring-boot:run`/local `mvnw
 * package` build with no {@code -Dbuild.commit} override) - safe to expose publicly, it's already
 * public in the git remote.
 */
public record PlatformStatusResponse(
    String status, String application, String version, String commit) {}
