package com.foreignerwarsaw.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backing type for {@code app.cors.allowed-origins}. Empty by default so CORS fails safe (denies
 * every cross-origin request) unless a profile explicitly configures origins - see application.yml
 * and ARCHITECTURE.md §11 / brief §15 ("never `*` in production").
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
