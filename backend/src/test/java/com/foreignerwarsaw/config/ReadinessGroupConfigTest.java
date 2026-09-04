package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Canonical Phase 14 (Observability) - a real, previously-undetected bug this phase found via a
 * production-like verification exercise (stopping the actual Postgres container while the app was
 * already serving traffic, then comparing {@code /actuator/health} against {@code
 * /actuator/health/readiness}): Spring Boot's {@code readiness} health group, by default, includes
 * ONLY its own internal {@code readinessState} indicator (which tracks graceful-shutdown/startup
 * {@code AvailabilityState}, not any actual dependency) - it does NOT automatically fold in {@code
 * db} or any other registered {@link org.springframework.boot.health.contributor.HealthIndicator}.
 * Every prior claim in this codebase's own docs that readiness means "database reachable"
 * (CLAUDE.md, {@code docs/operations/OBSERVABILITY.md}, {@code
 * docs/operations/INCIDENT_RESPONSE.md}) was therefore wrong in practice until this phase: a real
 * Postgres outage occurring AFTER a successful startup left {@code /actuator/health/readiness}
 * reporting {@code UP} indefinitely. Every prior failure exercise (Phase 13) only ever tested a DB
 * outage present at *startup* (which fails for the unrelated reason that Flyway/JPA can't
 * initialize, so the process never finishes starting at all) and never exercised this path.
 *
 * <p>This is a plain, no-Spring-context YAML-reading test (matching {@link ProductionConfigTest}'s
 * own pattern) - a fast, permanent guard against ever silently dropping {@code db} from the
 * readiness group again. The full dynamic proof (a real Postgres container stopped mid-test, {@code
 * /actuator/health/readiness} observed going to 503, then recovering) is a one-time production-like
 * verification exercise documented in {@code docs/product/PHASE_14_REPORT.md}, not repeated here as
 * an automated test - stopping/restarting a shared Testcontainers Postgres instance mid-suite would
 * be slow and would risk destabilizing every other integration test sharing that same container.
 */
class ReadinessGroupConfigTest {

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadBaseConfig() throws Exception {
    try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
      return new Yaml().load(in);
    }
  }

  @SuppressWarnings("unchecked")
  private Object path(Map<String, Object> yaml, String... keys) {
    Object current = yaml;
    for (String key : keys) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<String, Object>) current).get(key);
    }
    return current;
  }

  @Test
  void readinessGroupExplicitlyIncludesTheDatabaseHealthIndicator() throws Exception {
    Map<String, Object> yaml = loadBaseConfig();
    Object include =
        path(yaml, "management", "endpoint", "health", "group", "readiness", "include");

    assertThat(include).isNotNull();
    List<String> included = List.of(include.toString().split(","));
    assertThat(included).contains("db");
  }

  /**
   * The inverse of the readiness assertion above (brief's own "mail must never fail readiness") - a
   * future edit that widens the readiness group to include {@code mail} would reintroduce the exact
   * health-check anti-pattern {@code management.health.mail.enabled: false} already guards against.
   */
  @Test
  void readinessGroupNeverIncludesMail() throws Exception {
    Map<String, Object> yaml = loadBaseConfig();
    Object include =
        path(yaml, "management", "endpoint", "health", "group", "readiness", "include");

    assertThat(include).isNotNull();
    List<String> included = List.of(include.toString().split(","));
    assertThat(included).doesNotContain("mail");
  }
}
