package com.foreignerwarsaw.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application-level connectivity/diagnostic endpoint (brief §14), distinct in purpose from Spring
 * Boot Actuator's {@code /actuator/health}:
 *
 * <ul>
 *   <li>{@code /actuator/health} - infrastructure/orchestration health check (used by Docker
 *       healthchecks, uptime monitoring, load balancers).
 *   <li>{@code /api/v1/platform/status} - the one thing the Angular frontend calls to prove "API
 *       connected" and show which backend version it's talking to (brief §14 /
 *       IMPLEMENTATION_PLAN.md 1.9's frontend↔backend connectivity proof).
 * </ul>
 *
 * A separate {@code /api/v1/health} was considered and rejected for Phase 1 - Actuator health
 * already covers infra checks, and duplicating it under {@code /api/v1} would just be two paths for
 * the same fact with no distinct consumer.
 */
@RestController
public class PlatformStatusController {

  private final BuildProperties buildProperties;

  public PlatformStatusController(ObjectProvider<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties.getIfAvailable();
  }

  @GetMapping("/api/v1/platform/status")
  public PlatformStatusResponse status() {
    // BuildProperties is only populated when the app was built via `mvnw package`
    // (spring-boot:build-info); running via `mvnw spring-boot:run` in local dev
    // skips that goal, so this falls back to a clearly-labeled dev placeholder
    // rather than throwing.
    String version = buildProperties != null ? buildProperties.getVersion() : "dev-local";
    String commit = buildProperties != null ? buildProperties.get("commit") : null;
    return new PlatformStatusResponse(
        "UP", "Foreigner Warsaw", version, commit != null ? commit : "unknown");
  }
}
