package com.foreignerwarsaw;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Public (not package-private, as Initializr generates it by default) so every test
// package under com.foreignerwarsaw can @Import it, not just tests in the root
// package - see IMPLEMENTATION_PLAN.md §20.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    // Pinned to the project's target PostgreSQL major version (ADR-002) rather
    // than "latest", so integration tests fail loudly on a real incompatibility
    // instead of drifting silently when a new Postgres major version ships.
    // Note: org.testcontainers.postgresql.PostgreSQLContainer (the newer,
    // dedicated Testcontainers module) is not generic, unlike the older
    // org.testcontainers.containers.PostgreSQLContainer<SELF> - no <> here.
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
  }
}
