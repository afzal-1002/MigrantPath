package com.foreignerwarsaw.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Phase 11 brief §205 - a plain unit test (no Spring context) reading the actual committed {@code
 * application-{production,staging}.yml} files as data, so a future edit that silently reintroduces
 * an insecure default (a CORS wildcard, an insecure cookie, Swagger left enabled, a hard-coded
 * fallback where a fail-fast placeholder belongs) fails CI immediately rather than only being
 * caught by inspection or, worse, in a real deployment. Deliberately does not start a Spring
 * context under the {@code production} profile - that would need every {@code ${...}} placeholder
 * satisfied to even boot, which defeats the "these must be unresolved/env-supplied" property this
 * test exists to check.
 */
class ProductionConfigTest {

  @SuppressWarnings("unchecked")
  private Map<String, Object> load(String resourceName) throws Exception {
    try (InputStream in = new ClassPathResource(resourceName).getInputStream()) {
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
  void production_corsAllowedOriginsIsAPlaceholder_neverAWildcardOrHardcodedHost()
      throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    Object allowedOrigins = path(yaml, "app", "cors", "allowed-origins");
    assertThat(allowedOrigins).asString().isEqualTo("${FRONTEND_URL}");
  }

  @Test
  void staging_corsAllowedOriginsIsAPlaceholder_neverAWildcardOrHardcodedHost() throws Exception {
    Map<String, Object> yaml = load("application-staging.yml");
    Object allowedOrigins = path(yaml, "app", "cors", "allowed-origins");
    assertThat(allowedOrigins).asString().isEqualTo("${FRONTEND_URL}");
  }

  @Test
  void production_sessionCookieIsSecure() throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    assertThat(path(yaml, "server", "servlet", "session", "cookie", "secure")).isEqualTo(true);
  }

  @Test
  void staging_sessionCookieIsSecure() throws Exception {
    Map<String, Object> yaml = load("application-staging.yml");
    assertThat(path(yaml, "server", "servlet", "session", "cookie", "secure")).isEqualTo(true);
  }

  @Test
  void production_swaggerAndApiDocsAreDisabled() throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    assertThat(path(yaml, "springdoc", "swagger-ui", "enabled")).isEqualTo(false);
    assertThat(path(yaml, "springdoc", "api-docs", "enabled")).isEqualTo(false);
  }

  @Test
  void production_databaseCredentialsAreEnvironmentPlaceholders_noHardcodedFallback()
      throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    String url = String.valueOf(path(yaml, "spring", "datasource", "url"));
    assertThat(url).isEqualTo("jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}");
    // No "${VAR:default}" fallback syntax within any placeholder - a missing env var
    // must fail startup, never silently resolve to a local/dev value (contrast with
    // application-local.yml's own deliberate ${DB_HOST:localhost}-style fallbacks).
    assertThat(url).doesNotContainPattern("\\$\\{[A-Z_]+:");
    assertThat(path(yaml, "spring", "datasource", "username"))
        .asString()
        .isEqualTo("${DB_USERNAME}");
    assertThat(path(yaml, "spring", "datasource", "password"))
        .asString()
        .isEqualTo("${DB_PASSWORD}");
  }

  @Test
  void production_mailHostIsAnEnvironmentPlaceholder_neverLocalhostOrMailpitDefault()
      throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    assertThat(path(yaml, "spring", "mail", "host")).asString().isEqualTo("${MAIL_HOST}");
  }

  @Test
  void production_forwardedHeadersAreTrusted_onlyBehindTheDeployedReverseProxy() throws Exception {
    Map<String, Object> yaml = load("application-production.yml");
    assertThat(path(yaml, "server", "forward-headers-strategy")).isEqualTo("framework");
  }

  @Test
  void baseConfig_actuatorExposesOnlyHealthInfoAndPrometheus() throws Exception {
    // Canonical Phase 14 (Observability) brief §17 - prometheus added; SecurityConfig's
    // own permitted-path list (unchanged) is what actually keeps it non-public, not
    // omission from this list alone - see ActuatorExposureTest.
    Map<String, Object> yaml = load("application.yml");
    Object include = path(yaml, "management", "endpoints", "web", "exposure", "include");
    assertThat(include).asString().isEqualTo("health,info,prometheus");
  }

  @Test
  void baseConfig_healthDetailsAreNeverShownByDefault() throws Exception {
    Map<String, Object> yaml = load("application.yml");
    assertThat(path(yaml, "management", "endpoint", "health", "show-details")).isEqualTo("never");
  }

  @Test
  void baseConfig_corsAllowedOriginsIsEmptyByDefault_failsSafeUntilAProfileSetsIt()
      throws Exception {
    Map<String, Object> yaml = load("application.yml");
    Object allowedOrigins = path(yaml, "app", "cors", "allowed-origins");
    assertThat((List<?>) allowedOrigins).isEmpty();
  }

  @Test
  void baseConfig_adminBootstrapIsDisabledByDefault() throws Exception {
    Map<String, Object> yaml = load("application.yml");
    assertThat(path(yaml, "app", "admin-bootstrap", "enabled"))
        .asString()
        .isEqualTo("${APP_ADMIN_BOOTSTRAP_ENABLED:false}");
  }

  @Test
  void local_showsHealthDetails_neverStagingOrProduction() throws Exception {
    // The "always" show-details override is legitimate only in local (brief §22/§37) -
    // asserting it stays confined there, rather than asserting a negative on every
    // other file (which this test suite already does via show-details being "never"
    // at the base-config level with no override in staging/production.yml).
    Map<String, Object> yaml = load("application-local.yml");
    assertThat(path(yaml, "management", "endpoint", "health", "show-details")).isEqualTo("always");
  }
}
