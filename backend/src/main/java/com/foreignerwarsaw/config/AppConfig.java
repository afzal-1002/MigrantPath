package com.foreignerwarsaw.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A {@link Clock} bean, injected wherever "now" matters for token expiry/lockout logic, instead of
 * scattering {@code Instant.now()} calls - brief §36. Tests override this bean with {@link
 * Clock#fixed} to exercise "expired token" / "not yet expired" / "future timestamp" cases without
 * sleeping.
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, MailProperties.class})
@EnableCaching
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * RestAuthenticationEntryPoint/RestAccessDeniedHandler need an {@link ObjectMapper} to write
   * {@link com.foreignerwarsaw.common.web.ApiError} bodies from a filter-level security handler - a
   * context Spring MVC's auto-configured message-converter {@code ObjectMapper} is not reliably
   * exposed as an injectable bean from (Spring Framework 7 / Boot 4.1 default to a Jackson 3
   * ({@code tools.jackson}) message converter internally; this stays on Jackson 2 ({@code
   * com.fasterxml.jackson}, still what springdoc and the rest of this codebase use) explicitly
   * rather than guessing which major version Boot wires up as a bean). {@link JavaTimeModule} is
   * registered explicitly so {@link ApiError}'s {@code Instant timestamp} field serializes
   * correctly.
   */
  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
